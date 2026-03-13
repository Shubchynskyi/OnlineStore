package com.onlinestore.shipping.api;

import com.onlinestore.common.security.AuthenticatedUserResolver;
import com.onlinestore.shipping.dto.ShippingRateDTO;
import com.onlinestore.shipping.dto.ShipmentDTO;
import com.onlinestore.shipping.dto.TrackingEventDTO;
import com.onlinestore.shipping.provider.ShippingRequest;
import com.onlinestore.shipping.service.ShippingService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
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

    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final ShippingService shippingService;

    @PostMapping("/rates")
    public List<ShippingRateDTO> calculateRates(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody @Validated QuoteRatesRequest request
    ) {
        var userId = authenticatedUserResolver.resolve(jwt).requiredUserId();
        return shippingService.calculateRates(userId, request.orderId(), toShippingRequest(request), request.providerCode());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShipmentDTO createShipment(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody @Validated CreateShipmentRequest request
    ) {
        var userId = authenticatedUserResolver.resolve(jwt).requiredUserId();
        return shippingService.createShipment(
            userId,
            request.orderId(),
            toShippingRequest(request),
            request.providerCode(),
            request.rateCode()
        );
    }

    @GetMapping("/order/{orderId}")
    public ShipmentDTO getByOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId) {
        return shippingService.getByOrderId(authenticatedUserResolver.resolve(jwt).requiredUserId(), orderId);
    }

    @GetMapping("/{shipmentId}/tracking")
    public List<TrackingEventDTO> trackShipment(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shipmentId) {
        return shippingService.trackShipment(authenticatedUserResolver.resolve(jwt).requiredUserId(), shipmentId);
    }

    @PostMapping("/{shipmentId}/cancel")
    public ShipmentDTO cancelShipment(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shipmentId) {
        return shippingService.cancelShipment(authenticatedUserResolver.resolve(jwt).requiredUserId(), shipmentId);
    }

    private ShippingRequest toShippingRequest(ShippingRequestPayload request) {
        return new ShippingRequest(
            request.orderId(),
            request.destinationCountry(),
            request.destinationCity(),
            request.destinationPostalCode()
        );
    }

    private interface ShippingRequestPayload {
        Long orderId();

        String destinationCountry();

        String destinationCity();

        String destinationPostalCode();
    }

    public record QuoteRatesRequest(
        @NotNull Long orderId,
        String providerCode,
        @NotBlank String destinationCountry,
        @NotBlank String destinationCity,
        @NotBlank String destinationPostalCode
    ) implements ShippingRequestPayload {
    }

    public record CreateShipmentRequest(
        @NotNull Long orderId,
        @NotBlank String providerCode,
        String rateCode,
        @NotBlank String destinationCountry,
        @NotBlank String destinationCity,
        @NotBlank String destinationPostalCode
    ) implements ShippingRequestPayload {
    }
}
