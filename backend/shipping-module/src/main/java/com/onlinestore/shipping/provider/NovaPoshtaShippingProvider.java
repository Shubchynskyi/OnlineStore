package com.onlinestore.shipping.provider;

import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.shipping.entity.Shipment;
import com.onlinestore.shipping.entity.ShipmentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class NovaPoshtaShippingProvider implements ShippingProvider {

    private static final String PROVIDER_CODE = "nova_poshta";
    private static final Set<String> SUPPORTED_COUNTRIES = Set.of("UA");

    @Override
    public String getProviderCode() {
        return PROVIDER_CODE;
    }

    @Override
    public Set<String> getSupportedCountries() {
        return SUPPORTED_COUNTRIES;
    }

    @Override
    public List<ShippingRate> calculateRates(ShippingRequest request) {
        validateCountry(request);
        return List.of(
            new ShippingRate("nova_branch", PROVIDER_CODE, "Nova Poshta Branch Pickup", new BigDecimal("120.00"), "UAH", 2),
            new ShippingRate("nova_courier", PROVIDER_CODE, "Nova Poshta Courier", new BigDecimal("185.00"), "UAH", 1)
        );
    }

    @Override
    public Shipment createShipment(ShippingRequest request, ShippingRate selectedRate) {
        validateCountry(request);
        validateSelectedRate(selectedRate);

        var trackingNumber = "NP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        var shipment = new Shipment();
        shipment.setProviderCode(PROVIDER_CODE);
        shipment.setTrackingNumber(trackingNumber);
        shipment.setStatus(ShipmentStatus.LABEL_CREATED);
        shipment.setEstimatedDelivery(LocalDate.now().plusDays(selectedRate.estimatedDays()));
        shipment.setShippingCostAmount(selectedRate.amount());
        shipment.setShippingCostCurrency(selectedRate.currency());
        shipment.setLabelUrl("https://labels.novaposhta.example/" + trackingNumber + ".pdf");
        return shipment;
    }

    @Override
    public TrackingInfo track(String trackingNumber) {
        return new TrackingInfo(
            trackingNumber,
            ShipmentStatus.IN_TRANSIT,
            "Kyiv Distribution Center",
            "Shipment accepted by Nova Poshta",
            Instant.now()
        );
    }

    @Override
    public void cancelShipment(String shipmentId) {
        if (shipmentId == null || shipmentId.isBlank()) {
            throw new BusinessException("INVALID_SHIPMENT_ID", "Shipment id is required");
        }
    }

    private void validateCountry(ShippingRequest request) {
        String countryCode = request.destinationCountry().trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_COUNTRIES.contains(countryCode)) {
            throw new BusinessException("NO_SHIPPING_PROVIDER", "Nova Poshta does not support country: " + countryCode);
        }
    }

    private void validateSelectedRate(ShippingRate selectedRate) {
        if (!PROVIDER_CODE.equals(selectedRate.providerCode())) {
            throw new BusinessException("SHIPPING_RATE_NOT_AVAILABLE", "Selected rate does not belong to provider: " + PROVIDER_CODE);
        }
    }
}
