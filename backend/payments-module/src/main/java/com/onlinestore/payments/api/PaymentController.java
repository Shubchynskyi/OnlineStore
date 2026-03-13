package com.onlinestore.payments.api;

import com.onlinestore.common.security.AuthenticatedUserResolver;
import com.onlinestore.payments.dto.ConfirmPaymentRequest;
import com.onlinestore.payments.dto.InitiatePaymentRequest;
import com.onlinestore.payments.dto.PaymentDTO;
import com.onlinestore.payments.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final PaymentService paymentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentDTO initiate(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody InitiatePaymentRequest request
    ) {
        return paymentService.initiatePayment(authenticatedUserResolver.resolve(jwt).requiredUserId(), request);
    }

    @PostMapping("/{id}/confirm")
    @ResponseStatus(HttpStatus.OK)
    public PaymentDTO confirm(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id,
        @Valid @RequestBody ConfirmPaymentRequest request
    ) {
        return paymentService.confirmPayment(authenticatedUserResolver.resolve(jwt).requiredUserId(), id, request);
    }
}
