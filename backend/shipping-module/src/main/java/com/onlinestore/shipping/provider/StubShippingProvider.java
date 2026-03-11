package com.onlinestore.shipping.provider;

import com.onlinestore.shipping.entity.ShipmentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class StubShippingProvider implements ShippingProvider {

    @Override
    public String getProviderCode() {
        return "stub";
    }

    @Override
    public Set<String> getSupportedCountries() {
        return Set.of("US", "DE", "GB", "FR", "IT", "ES", "NL", "PL", "UA");
    }

    @Override
    public ShippingRate calculateRate(ShippingRequest request) {
        return new ShippingRate(getProviderCode(), BigDecimal.valueOf(9.99), "EUR", 5);
    }

    @Override
    public String createShipment(ShippingRequest request) {
        return "stub-" + UUID.randomUUID();
    }

    @Override
    public List<TrackingInfo> track(String trackingNumber) {
        return List.of(new TrackingInfo(
            trackingNumber,
            ShipmentStatus.IN_TRANSIT,
            "Sorting Center",
            "Shipment in transit",
            Instant.now()
        ));
    }
}
