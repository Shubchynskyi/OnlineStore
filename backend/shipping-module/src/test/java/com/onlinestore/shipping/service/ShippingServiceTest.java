package com.onlinestore.shipping.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.common.port.orders.OrderAccessGateway;
import com.onlinestore.common.port.orders.OrderAccessView;
import com.onlinestore.shipping.provider.ShippingRequest;
import com.onlinestore.shipping.registry.ShippingProviderRegistry;
import com.onlinestore.shipping.repository.ShipmentRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShippingServiceTest {

    @Mock
    private ShipmentRepository shipmentRepository;
    @Mock
    private OrderAccessGateway orderAccessGateway;
    @Mock
    private ShippingProviderRegistry providerRegistry;

    private ShippingService shippingService;

    @BeforeEach
    void setUp() {
        shippingService = new ShippingService(shipmentRepository, orderAccessGateway, providerRegistry);
    }

    @Test
    void createShipmentShouldRejectForeignOrder() {
        var order = new OrderAccessView(100L, 25L, null, null);
        when(orderAccessGateway.findById(100L)).thenReturn(Optional.of(order));

        var request = new ShippingRequest(100L, "DE", "Berlin", "10115");
        assertThrows(BusinessException.class, () -> shippingService.createShipment(7L, 100L, request, "stub"));

        verify(providerRegistry, never()).getByCode("stub");
        verify(shipmentRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getByOrderShouldRejectForeignOrder() {
        var order = new OrderAccessView(101L, 55L, null, null);
        when(orderAccessGateway.findById(101L)).thenReturn(Optional.of(order));

        assertThrows(BusinessException.class, () -> shippingService.getByOrderId(7L, 101L));

        verify(shipmentRepository, never()).findByOrderId(101L);
    }
}
