package com.onlinestore.payments.api;

import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.payments.dto.InitiatePaymentRequest;
import com.onlinestore.payments.dto.PaymentDTO;
import com.onlinestore.payments.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentDTO initiate(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody InitiatePaymentRequest request
    ) {
        return paymentService.initiatePayment(extractUserId(jwt), request);
    }

    private Long extractUserId(Jwt jwt) {
        String claimValue = jwt.getClaimAsString("user_id");
        if (claimValue == null || claimValue.isBlank()) {
            throw new BusinessException("INVALID_TOKEN", "user_id claim is missing");
        }
        try {
            return Long.parseLong(claimValue);
        } catch (NumberFormatException ex) {
            throw new BusinessException("INVALID_TOKEN", "user_id claim has invalid format");
        }
    }
}
