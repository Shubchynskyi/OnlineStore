package com.onlinestore.shipping.api;

import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.shipping.dto.ShipmentDTO;
import com.onlinestore.shipping.provider.ShippingRequest;
import com.onlinestore.shipping.service.ShippingService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shipping")
@RequiredArgsConstructor
@Validated
public class ShippingController {

    private final ShippingService shippingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShipmentDTO createShipment(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody @Validated CreateShipmentRequest request
    ) {
        var userId = extractUserId(jwt);
        var shippingRequest = new ShippingRequest(
            request.orderId(),
            request.destinationCountry(),
            request.destinationCity(),
            request.destinationPostalCode()
        );
        return shippingService.createShipment(userId, request.orderId(), shippingRequest, request.providerCode());
    }

    @GetMapping("/order/{orderId}")
    public ShipmentDTO getByOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId) {
        return shippingService.getByOrderId(extractUserId(jwt), orderId);
    }

    public record CreateShipmentRequest(
        @NotNull Long orderId,
        @NotBlank String providerCode,
        @NotBlank String destinationCountry,
        @NotBlank String destinationCity,
        @NotBlank String destinationPostalCode
    ) {
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
