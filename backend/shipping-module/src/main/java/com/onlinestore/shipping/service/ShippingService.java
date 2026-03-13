package com.onlinestore.shipping.service;

import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.common.exception.ResourceNotFoundException;
import com.onlinestore.common.port.orders.OrderAccessGateway;
import com.onlinestore.shipping.dto.ShippingRateDTO;
import com.onlinestore.shipping.dto.ShipmentDTO;
import com.onlinestore.shipping.dto.TrackingEventDTO;
import com.onlinestore.shipping.entity.Shipment;
import com.onlinestore.shipping.entity.ShipmentStatus;
import com.onlinestore.shipping.entity.TrackingEvent;
import com.onlinestore.shipping.provider.ShippingRequest;
import com.onlinestore.shipping.provider.ShippingRate;
import com.onlinestore.shipping.provider.TrackingInfo;
import com.onlinestore.shipping.registry.ShippingProviderRegistry;
import com.onlinestore.shipping.repository.ShipmentRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShippingService {

    private final ShipmentRepository shipmentRepository;
    private final OrderAccessGateway orderAccessGateway;
    private final ShippingProviderRegistry providerRegistry;

    @Transactional(readOnly = true)
    public List<ShippingRateDTO> calculateRates(Long userId, Long orderId, ShippingRequest request, String providerCode) {
        validateOrderAccess(orderId, userId);

        if (StringUtils.hasText(providerCode)) {
            return providerRegistry.getByCode(providerCode).calculateRates(request).stream()
                .map(this::toRateDto)
                .toList();
        }

        var rates = providerRegistry.getProvidersForCountry(request.destinationCountry()).stream()
            .flatMap(provider -> provider.calculateRates(request).stream())
            .map(this::toRateDto)
            .toList();

        if (rates.isEmpty()) {
            throw new BusinessException(
                "NO_SHIPPING_PROVIDER",
                "No shipping provider available for country: " + request.destinationCountry()
            );
        }

        return rates;
    }

    @Transactional
    public ShipmentDTO createShipment(Long userId, Long orderId, ShippingRequest request, String providerCode, String rateCode) {
        validateOrderAccess(orderId, userId);

        var provider = providerRegistry.getByCode(providerCode);
        var selectedRate = resolveSelectedRate(provider.calculateRates(request), providerCode, rateCode);
        var shipment = shipmentRepository.findByOrderId(orderId)
            .map(this::prepareReusableShipment)
            .orElseGet(Shipment::new);
        var providerShipment = provider.createShipment(request, selectedRate);

        applyProviderShipment(shipment, orderId, providerShipment);
        addTrackingEvent(
            shipment,
            shipment.getStatus(),
            providerCode.toUpperCase(Locale.ROOT),
            "Shipping label created",
            Instant.now()
        );

        var saved = shipmentRepository.save(shipment);
        log.info(
            "Shipment created: shipmentId={}, orderId={}, userId={}, provider={}, rateCode={}",
            saved.getId(),
            orderId,
            userId,
            providerCode,
            selectedRate.rateCode()
        );
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public ShipmentDTO getByOrderId(Long userId, Long orderId) {
        validateOrderAccess(orderId, userId);

        return shipmentRepository.findByOrderId(orderId)
            .map(this::toDto)
            .orElseThrow(() -> new ResourceNotFoundException("Shipment", "orderId", orderId));
    }

    @Transactional
    public List<TrackingEventDTO> trackShipment(Long userId, Long shipmentId) {
        var shipment = getShipmentForUser(shipmentId, userId);

        if (shipment.getStatus() != ShipmentStatus.CANCELLED && StringUtils.hasText(shipment.getTrackingNumber())) {
            var provider = providerRegistry.getProviderForExistingShipment(shipment.getProviderCode());
            var latestTrackingInfo = provider.track(shipment.getTrackingNumber());
            if (synchronizeTrackingState(shipment, latestTrackingInfo)) {
                shipmentRepository.save(shipment);
            }
        }

        return shipment.getTrackingEvents().stream()
            .sorted(Comparator.comparing(TrackingEvent::getOccurredAt))
            .map(this::toTrackingEventDto)
            .toList();
    }

    @Transactional
    public ShipmentDTO cancelShipment(Long userId, Long shipmentId) {
        var shipment = getShipmentForUser(shipmentId, userId);

        if (shipment.getStatus() != ShipmentStatus.PENDING && shipment.getStatus() != ShipmentStatus.LABEL_CREATED) {
            throw new BusinessException(
                "SHIPMENT_CANNOT_BE_CANCELLED",
                "Shipment cannot be cancelled in status: " + shipment.getStatus()
            );
        }

        if (StringUtils.hasText(shipment.getTrackingNumber())) {
            providerRegistry.getProviderForExistingShipment(shipment.getProviderCode())
                .cancelShipment(shipment.getTrackingNumber());
        }

        shipment.setStatus(ShipmentStatus.CANCELLED);
        addTrackingEvent(shipment, ShipmentStatus.CANCELLED, null, "Shipment cancelled by customer", Instant.now());

        var saved = shipmentRepository.save(shipment);
        log.info("Shipment cancelled: shipmentId={}, orderId={}, userId={}", saved.getId(), saved.getOrderId(), userId);
        return toDto(saved);
    }

    private void validateOrderAccess(Long orderId, Long userId) {
        var order = orderAccessGateway.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (!order.userId().equals(userId)) {
            throw new BusinessException("ACCESS_DENIED", "Order belongs to another user");
        }
    }

    private Shipment getShipmentForUser(Long shipmentId, Long userId) {
        var shipment = shipmentRepository.findById(shipmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Shipment", "id", shipmentId));
        validateOrderAccess(shipment.getOrderId(), userId);
        return shipment;
    }

    private Shipment prepareReusableShipment(Shipment shipment) {
        if (shipment.getStatus() != ShipmentStatus.CANCELLED && shipment.getStatus() != ShipmentStatus.FAILED) {
            throw new BusinessException(
                "SHIPMENT_ALREADY_EXISTS",
                "Shipment already exists for order: " + shipment.getOrderId()
            );
        }

        shipment.getTrackingEvents().clear();
        return shipment;
    }

    private ShippingRate resolveSelectedRate(List<ShippingRate> availableRates, String providerCode, String rateCode) {
        if (availableRates.isEmpty()) {
            throw new BusinessException(
                "SHIPPING_RATE_NOT_AVAILABLE",
                "No shipping rates available for provider: " + providerCode
            );
        }

        if (StringUtils.hasText(rateCode)) {
            return availableRates.stream()
                .filter(rate -> rate.rateCode().equalsIgnoreCase(rateCode.trim()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                    "SHIPPING_RATE_NOT_AVAILABLE",
                    "Shipping rate is not available for provider: " + providerCode
                ));
        }

        return availableRates.stream()
            .min(Comparator.comparing(ShippingRate::amount))
            .orElseThrow(() -> new BusinessException(
                "SHIPPING_RATE_NOT_AVAILABLE",
                "Shipping rate is not available for provider: " + providerCode
            ));
    }

    private void applyProviderShipment(Shipment target, Long orderId, Shipment source) {
        target.setOrderId(orderId);
        target.setProviderCode(source.getProviderCode());
        target.setTrackingNumber(source.getTrackingNumber());
        target.setStatus(source.getStatus());
        target.setEstimatedDelivery(source.getEstimatedDelivery());
        target.setShippingCostAmount(source.getShippingCostAmount());
        target.setShippingCostCurrency(source.getShippingCostCurrency());
        target.setLabelUrl(source.getLabelUrl());
        target.getTrackingEvents().clear();
    }

    private boolean synchronizeTrackingState(Shipment shipment, TrackingInfo trackingInfo) {
        var latestEvent = shipment.getTrackingEvents().stream()
            .max(Comparator.comparing(TrackingEvent::getOccurredAt))
            .orElse(null);

        boolean matchesLatestEvent = latestEvent != null
            && Objects.equals(latestEvent.getStatus(), trackingInfo.status().name())
            && Objects.equals(latestEvent.getLocation(), trackingInfo.location())
            && Objects.equals(latestEvent.getDescription(), trackingInfo.description());

        if (matchesLatestEvent) {
            boolean statusChanged = shipment.getStatus() != trackingInfo.status();
            shipment.setStatus(trackingInfo.status());
            return statusChanged;
        }

        boolean existsInHistory = shipment.getTrackingEvents().stream()
            .anyMatch(event -> Objects.equals(event.getStatus(), trackingInfo.status().name())
                && Objects.equals(event.getLocation(), trackingInfo.location())
                && Objects.equals(event.getDescription(), trackingInfo.description()));
        if (existsInHistory) {
            boolean statusChanged = shipment.getStatus() != trackingInfo.status();
            shipment.setStatus(trackingInfo.status());
            return statusChanged;
        }

        shipment.setStatus(trackingInfo.status());

        addTrackingEvent(
            shipment,
            trackingInfo.status(),
            trackingInfo.location(),
            trackingInfo.description(),
            trackingInfo.occurredAt()
        );
        return true;
    }

    private void addTrackingEvent(
        Shipment shipment,
        ShipmentStatus status,
        String location,
        String description,
        Instant occurredAt
    ) {
        var event = new TrackingEvent();
        event.setShipment(shipment);
        event.setStatus(status.name());
        event.setLocation(location);
        event.setDescription(description);
        event.setOccurredAt(occurredAt);
        shipment.getTrackingEvents().add(event);
    }

    private ShippingRateDTO toRateDto(ShippingRate rate) {
        return new ShippingRateDTO(
            rate.rateCode(),
            rate.providerCode(),
            rate.serviceName(),
            rate.amount(),
            rate.currency(),
            rate.estimatedDays()
        );
    }

    private ShipmentDTO toDto(Shipment shipment) {
        return new ShipmentDTO(
            shipment.getId(),
            shipment.getOrderId(),
            shipment.getProviderCode(),
            shipment.getTrackingNumber(),
            shipment.getStatus(),
            shipment.getEstimatedDelivery(),
            shipment.getLabelUrl(),
            shipment.getShippingCostAmount(),
            shipment.getShippingCostCurrency()
        );
    }

    private TrackingEventDTO toTrackingEventDto(TrackingEvent event) {
        return new TrackingEventDTO(
            ShipmentStatus.valueOf(event.getStatus()),
            event.getLocation(),
            event.getDescription(),
            event.getOccurredAt()
        );
    }
}
