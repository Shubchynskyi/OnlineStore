package com.onlinestore.shipping.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onlinestore.shipping.entity.ShipmentStatus;
import org.junit.jupiter.api.Test;

class DhlEuropeShippingProviderTest {

    private final DhlEuropeShippingProvider provider = new DhlEuropeShippingProvider();

    @Test
    void calculateRatesShouldReturnEconomyAndExpressOptions() {
        var rates = provider.calculateRates(new ShippingRequest(1L, "DE", "Berlin", "10115"));

        assertEquals(2, rates.size());
        assertEquals("dhl_economy", rates.get(0).rateCode());
        assertEquals("dhl_express", rates.get(1).rateCode());
        assertEquals("EUR", rates.get(0).currency());
    }

    @Test
    void createShipmentShouldUseSelectedRateAttributes() {
        var request = new ShippingRequest(1L, "DE", "Berlin", "10115");
        var selectedRate = provider.calculateRates(request).get(1);

        var shipment = provider.createShipment(request, selectedRate);

        assertEquals("dhl", shipment.getProviderCode());
        assertEquals(ShipmentStatus.LABEL_CREATED, shipment.getStatus());
        assertEquals(selectedRate.amount(), shipment.getShippingCostAmount());
        assertTrue(shipment.getLabelUrl().contains(shipment.getTrackingNumber()));
    }
}
