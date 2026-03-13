package com.onlinestore.shipping.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.shipping.entity.ShipmentStatus;
import org.junit.jupiter.api.Test;

class NovaPoshtaShippingProviderTest {

    private final NovaPoshtaShippingProvider provider = new NovaPoshtaShippingProvider();

    @Test
    void calculateRatesShouldReturnUkrainianDeliveryOptions() {
        var rates = provider.calculateRates(new ShippingRequest(1L, "UA", "Kyiv", "01001"));

        assertEquals(2, rates.size());
        assertEquals("nova_branch", rates.get(0).rateCode());
        assertEquals("UAH", rates.get(0).currency());
    }

    @Test
    void calculateRatesShouldRejectUnsupportedCountries() {
        var exception = assertThrows(
            BusinessException.class,
            () -> provider.calculateRates(new ShippingRequest(1L, "PL", "Warsaw", "00-001"))
        );

        assertEquals("NO_SHIPPING_PROVIDER", exception.getErrorCode());
    }

    @Test
    void createShipmentShouldUseSelectedRateAttributes() {
        var request = new ShippingRequest(1L, "UA", "Kyiv", "01001");
        var selectedRate = provider.calculateRates(request).get(0);

        var shipment = provider.createShipment(request, selectedRate);

        assertEquals("nova_poshta", shipment.getProviderCode());
        assertEquals(ShipmentStatus.LABEL_CREATED, shipment.getStatus());
        assertEquals(selectedRate.amount(), shipment.getShippingCostAmount());
    }
}
