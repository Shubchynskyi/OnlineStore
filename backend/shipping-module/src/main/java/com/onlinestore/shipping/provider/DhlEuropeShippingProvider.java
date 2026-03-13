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
public class DhlEuropeShippingProvider implements ShippingProvider {

    private static final String PROVIDER_CODE = "dhl";
    private static final Set<String> SUPPORTED_COUNTRIES = Set.of("AT", "BE", "DE", "ES", "FR", "IT", "NL", "PL");

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
            new ShippingRate("dhl_economy", PROVIDER_CODE, "DHL Economy Select", new BigDecimal("11.90"), "EUR", 4),
            new ShippingRate("dhl_express", PROVIDER_CODE, "DHL Express Worldwide", new BigDecimal("18.50"), "EUR", 2)
        );
    }

    @Override
    public Shipment createShipment(ShippingRequest request, ShippingRate selectedRate) {
        validateCountry(request);
        validateSelectedRate(selectedRate);

        var trackingNumber = "DHL-EU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        var shipment = new Shipment();
        shipment.setProviderCode(PROVIDER_CODE);
        shipment.setTrackingNumber(trackingNumber);
        shipment.setStatus(ShipmentStatus.LABEL_CREATED);
        shipment.setEstimatedDelivery(LocalDate.now().plusDays(selectedRate.estimatedDays()));
        shipment.setShippingCostAmount(selectedRate.amount());
        shipment.setShippingCostCurrency(selectedRate.currency());
        shipment.setLabelUrl("https://labels.dhl.example/" + trackingNumber + ".pdf");
        return shipment;
    }

    @Override
    public TrackingInfo track(String trackingNumber) {
        return new TrackingInfo(
            trackingNumber,
            ShipmentStatus.IN_TRANSIT,
            "DHL Europe Hub",
            "Shipment processed by DHL Europe",
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
            throw new BusinessException("NO_SHIPPING_PROVIDER", "DHL does not support country: " + countryCode);
        }
    }

    private void validateSelectedRate(ShippingRate selectedRate) {
        if (!PROVIDER_CODE.equals(selectedRate.providerCode())) {
            throw new BusinessException("SHIPPING_RATE_NOT_AVAILABLE", "Selected rate does not belong to provider: " + PROVIDER_CODE);
        }
    }
}
