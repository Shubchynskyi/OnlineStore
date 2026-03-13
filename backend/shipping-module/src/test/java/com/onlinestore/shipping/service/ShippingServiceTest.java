package com.onlinestore.shipping.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.common.port.orders.OrderAccessGateway;
import com.onlinestore.common.port.orders.OrderAccessView;
import com.onlinestore.shipping.entity.Shipment;
import com.onlinestore.shipping.entity.ShipmentStatus;
import com.onlinestore.shipping.entity.TrackingEvent;
import com.onlinestore.shipping.provider.ShippingProvider;
import com.onlinestore.shipping.provider.ShippingRequest;
import com.onlinestore.shipping.provider.ShippingRate;
import com.onlinestore.shipping.provider.TrackingInfo;
import com.onlinestore.shipping.registry.ShippingProviderRegistry;
import com.onlinestore.shipping.repository.ShipmentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    @Mock
    private ShippingProvider shippingProvider;

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
        assertThrows(BusinessException.class, () -> shippingService.createShipment(7L, 100L, request, "stub", null));

        verify(providerRegistry, never()).getByCode("stub");
        verify(shipmentRepository, never()).save(any());
    }

    @Test
    void getByOrderShouldRejectForeignOrder() {
        var order = new OrderAccessView(101L, 55L, null, null);
        when(orderAccessGateway.findById(101L)).thenReturn(Optional.of(order));

        assertThrows(BusinessException.class, () -> shippingService.getByOrderId(7L, 101L));

        verify(shipmentRepository, never()).findByOrderId(101L);
    }

    @Test
    void calculateRatesShouldReturnProviderRatesForOwnedOrder() {
        var order = new OrderAccessView(100L, 7L, null, null);
        var request = new ShippingRequest(100L, "DE", "Berlin", "10115");
        when(orderAccessGateway.findById(100L)).thenReturn(Optional.of(order));
        when(providerRegistry.getByCode("dhl")).thenReturn(shippingProvider);
        when(shippingProvider.calculateRates(request)).thenReturn(List.of(
            new ShippingRate("dhl_economy", "dhl", "DHL Economy", new BigDecimal("11.90"), "EUR", 4),
            new ShippingRate("dhl_express", "dhl", "DHL Express", new BigDecimal("18.50"), "EUR", 2)
        ));

        var rates = shippingService.calculateRates(7L, 100L, request, "dhl");

        assertEquals(2, rates.size());
        assertEquals("dhl_economy", rates.get(0).rateCode());
    }

    @Test
    void createShipmentShouldPersistSelectedRateUsingCheapestFallback() {
        var order = new OrderAccessView(100L, 7L, null, null);
        var request = new ShippingRequest(100L, "DE", "Berlin", "10115");
        var expressRate = new ShippingRate("dhl_express", "dhl", "DHL Express", new BigDecimal("18.50"), "EUR", 2);
        var economyRate = new ShippingRate("dhl_economy", "dhl", "DHL Economy", new BigDecimal("11.90"), "EUR", 4);
        var providerShipment = new Shipment();
        providerShipment.setProviderCode("dhl");
        providerShipment.setTrackingNumber("DHL-123");
        providerShipment.setStatus(ShipmentStatus.LABEL_CREATED);
        providerShipment.setEstimatedDelivery(LocalDate.now().plusDays(4));
        providerShipment.setShippingCostAmount(economyRate.amount());
        providerShipment.setShippingCostCurrency(economyRate.currency());
        providerShipment.setLabelUrl("https://labels.dhl.example/DHL-123.pdf");

        when(orderAccessGateway.findById(100L)).thenReturn(Optional.of(order));
        when(providerRegistry.getByCode("dhl")).thenReturn(shippingProvider);
        when(shippingProvider.calculateRates(request)).thenReturn(List.of(expressRate, economyRate));
        when(shippingProvider.createShipment(request, economyRate)).thenReturn(providerShipment);
        when(shipmentRepository.findByOrderId(100L)).thenReturn(Optional.empty());
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> {
            var shipment = invocation.getArgument(0, Shipment.class);
            shipment.setId(1L);
            return shipment;
        });

        var shipment = shippingService.createShipment(7L, 100L, request, "dhl", null);

        assertEquals("dhl", shipment.providerCode());
        assertEquals(new BigDecimal("11.90"), shipment.shippingCostAmount());

        var shipmentCaptor = ArgumentCaptor.forClass(Shipment.class);
        verify(shipmentRepository).save(shipmentCaptor.capture());
        assertEquals(1, shipmentCaptor.getValue().getTrackingEvents().size());
    }

    @Test
    void cancelShipmentShouldRejectForeignOrder() {
        var order = new OrderAccessView(100L, 25L, null, null);
        var shipment = new Shipment();
        shipment.setId(1L);
        shipment.setOrderId(100L);
        shipment.setProviderCode("dhl");
        shipment.setTrackingNumber("DHL-123");
        shipment.setStatus(ShipmentStatus.LABEL_CREATED);

        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(shipment));
        when(orderAccessGateway.findById(100L)).thenReturn(Optional.of(order));

        assertThrows(BusinessException.class, () -> shippingService.cancelShipment(7L, 1L));

        verify(providerRegistry, never()).getProviderForExistingShipment("dhl");
    }

    @Test
    void cancelShipmentShouldMarkShipmentAsCancelled() {
        var order = new OrderAccessView(100L, 7L, null, null);
        var shipment = new Shipment();
        shipment.setId(1L);
        shipment.setOrderId(100L);
        shipment.setProviderCode("dhl");
        shipment.setTrackingNumber("DHL-123");
        shipment.setStatus(ShipmentStatus.LABEL_CREATED);

        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(shipment));
        when(orderAccessGateway.findById(100L)).thenReturn(Optional.of(order));
        when(providerRegistry.getProviderForExistingShipment("dhl")).thenReturn(shippingProvider);
        when(shipmentRepository.save(shipment)).thenReturn(shipment);

        var cancelledShipment = shippingService.cancelShipment(7L, 1L);

        assertEquals(ShipmentStatus.CANCELLED, cancelledShipment.status());
        verify(shippingProvider).cancelShipment("DHL-123");
    }

    @Test
    void cancelShipmentShouldSkipProviderCallWhenTrackingNumberIsMissing() {
        var order = new OrderAccessView(100L, 7L, null, null);
        var shipment = new Shipment();
        shipment.setId(1L);
        shipment.setOrderId(100L);
        shipment.setProviderCode("dhl");
        shipment.setStatus(ShipmentStatus.PENDING);

        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(shipment));
        when(orderAccessGateway.findById(100L)).thenReturn(Optional.of(order));
        when(shipmentRepository.save(shipment)).thenReturn(shipment);

        var cancelledShipment = shippingService.cancelShipment(7L, 1L);

        assertEquals(ShipmentStatus.CANCELLED, cancelledShipment.status());
        verify(providerRegistry, never()).getProviderForExistingShipment("dhl");
    }

    @Test
    void trackShipmentShouldAppendLatestProviderEvent() {
        var order = new OrderAccessView(100L, 7L, null, null);
        var shipment = new Shipment();
        shipment.setId(1L);
        shipment.setOrderId(100L);
        shipment.setProviderCode("dhl");
        shipment.setTrackingNumber("DHL-123");
        shipment.setStatus(ShipmentStatus.LABEL_CREATED);

        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(shipment));
        when(orderAccessGateway.findById(100L)).thenReturn(Optional.of(order));
        when(providerRegistry.getProviderForExistingShipment("dhl")).thenReturn(shippingProvider);
        when(shippingProvider.track("DHL-123")).thenReturn(new TrackingInfo(
            "DHL-123",
            ShipmentStatus.IN_TRANSIT,
            "DHL Europe Hub",
            "Shipment processed by DHL Europe",
            Instant.parse("2026-03-13T12:00:00Z")
        ));

        var trackingEvents = shippingService.trackShipment(7L, 1L);

        assertEquals(1, trackingEvents.size());
        assertEquals(ShipmentStatus.IN_TRANSIT, trackingEvents.get(0).status());
        assertEquals(ShipmentStatus.IN_TRANSIT, shipment.getStatus());
    }

    @Test
    void trackShipmentShouldRefreshStatusWhenMatchingEventAlreadyExistsInHistory() {
        var order = new OrderAccessView(100L, 7L, null, null);
        var shipment = new Shipment();
        shipment.setId(1L);
        shipment.setOrderId(100L);
        shipment.setProviderCode("dhl");
        shipment.setTrackingNumber("DHL-123");
        shipment.setStatus(ShipmentStatus.LABEL_CREATED);

        var trackingEvent = new TrackingEvent();
        trackingEvent.setShipment(shipment);
        trackingEvent.setStatus(ShipmentStatus.IN_TRANSIT.name());
        trackingEvent.setLocation("DHL Europe Hub");
        trackingEvent.setDescription("Shipment processed by DHL Europe");
        trackingEvent.setOccurredAt(Instant.parse("2026-03-13T10:00:00Z"));
        shipment.getTrackingEvents().add(trackingEvent);

        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(shipment));
        when(orderAccessGateway.findById(100L)).thenReturn(Optional.of(order));
        when(providerRegistry.getProviderForExistingShipment("dhl")).thenReturn(shippingProvider);
        when(shippingProvider.track("DHL-123")).thenReturn(new TrackingInfo(
            "DHL-123",
            ShipmentStatus.IN_TRANSIT,
            "DHL Europe Hub",
            "Shipment processed by DHL Europe",
            Instant.parse("2026-03-13T12:00:00Z")
        ));

        var trackingEvents = shippingService.trackShipment(7L, 1L);

        assertEquals(1, trackingEvents.size());
        assertEquals(ShipmentStatus.IN_TRANSIT, shipment.getStatus());
    }
}
