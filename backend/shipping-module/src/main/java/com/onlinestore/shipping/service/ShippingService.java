package com.onlinestore.shipping.service;

import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.common.exception.ResourceNotFoundException;
import com.onlinestore.common.port.orders.OrderAccessGateway;
import com.onlinestore.shipping.dto.ShipmentDTO;
import com.onlinestore.shipping.entity.Shipment;
import com.onlinestore.shipping.entity.ShipmentStatus;
import com.onlinestore.shipping.provider.ShippingRequest;
import com.onlinestore.shipping.registry.ShippingProviderRegistry;
import com.onlinestore.shipping.repository.ShipmentRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShippingService {

    private final ShipmentRepository shipmentRepository;
    private final OrderAccessGateway orderAccessGateway;
    private final ShippingProviderRegistry providerRegistry;

    @Transactional
    public ShipmentDTO createShipment(Long userId, Long orderId, ShippingRequest request, String providerCode) {
        validateOrderAccess(orderId, userId);

        var provider = providerRegistry.getByCode(providerCode);
        var rate = provider.calculateRate(request);
        var trackingNumber = provider.createShipment(request);

        var shipment = new Shipment();
        shipment.setOrderId(orderId);
        shipment.setProviderCode(providerCode);
        shipment.setTrackingNumber(trackingNumber);
        shipment.setStatus(ShipmentStatus.LABEL_CREATED);
        shipment.setEstimatedDelivery(LocalDate.now().plusDays(rate.estimatedDays()));
        shipment.setShippingCostAmount(rate.amount());
        shipment.setShippingCostCurrency(rate.currency());
        shipment.setLabelUrl("https://labels.local/" + trackingNumber);
        var saved = shipmentRepository.save(shipment);
        log.info("Shipment created: shipmentId={}, orderId={}, userId={}, provider={}",
            saved.getId(),
            orderId,
            userId,
            providerCode);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public ShipmentDTO getByOrderId(Long userId, Long orderId) {
        validateOrderAccess(orderId, userId);

        return shipmentRepository.findByOrderId(orderId)
            .map(this::toDto)
            .orElseThrow(() -> new ResourceNotFoundException("Shipment", "orderId", orderId));
    }

    private void validateOrderAccess(Long orderId, Long userId) {
        var order = orderAccessGateway.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (!order.userId().equals(userId)) {
            throw new BusinessException("ACCESS_DENIED", "Order belongs to another user");
        }
    }

    private ShipmentDTO toDto(Shipment shipment) {
        return new ShipmentDTO(
            shipment.getId(),
            shipment.getOrderId(),
            shipment.getProviderCode(),
            shipment.getTrackingNumber(),
            shipment.getStatus(),
            shipment.getEstimatedDelivery(),
            shipment.getLabelUrl()
        );
    }
}
