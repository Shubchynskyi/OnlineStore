package com.onlinestore.orders.service;

import com.onlinestore.common.config.RabbitMQConfig;
import com.onlinestore.common.dto.PageResponse;
import com.onlinestore.common.event.OutboxService;
import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.common.exception.ResourceNotFoundException;
import com.onlinestore.common.port.address.AddressAccessGateway;
import com.onlinestore.common.port.catalog.ProductVariantGateway;
import com.onlinestore.orders.dto.CreateOrderRequest;
import com.onlinestore.orders.dto.OrderDTO;
import com.onlinestore.orders.dto.OrderItemRequest;
import com.onlinestore.orders.entity.Order;
import com.onlinestore.orders.entity.OrderEvent;
import com.onlinestore.orders.entity.OrderItem;
import com.onlinestore.orders.entity.OrderStatus;
import com.onlinestore.orders.entity.OrderStatusHistory;
import com.onlinestore.orders.mapper.OrderMapper;
import com.onlinestore.orders.repository.OrderRepository;
import com.onlinestore.orders.statemachine.OrderStateMachineConfig;
import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductVariantGateway productVariantGateway;
    private final AddressAccessGateway addressAccessGateway;
    private final OrderMapper orderMapper;
    private final OrderStateMachineConfig stateMachineConfig;
    private final OutboxService outboxService;

    @Transactional
    public OrderDTO createOrder(Long userId, CreateOrderRequest request) {
        if (!addressAccessGateway.isAddressOwnedByUser(request.shippingAddressId(), userId)) {
            throw new BusinessException("ADDRESS_ACCESS_DENIED", "Shipping address is not available for current user");
        }

        var order = new Order();
        order.setUserId(userId);
        order.setShippingAddressId(request.shippingAddressId());
        order.setNotes(request.notes());

        var variantIds = request.items().stream()
            .map(OrderItemRequest::productVariantId)
            .distinct()
            .toList();
        Map<Long, com.onlinestore.common.port.catalog.ProductVariantOrderView> variantsById =
            productVariantGateway.findByIds(variantIds);
        Map<Long, Integer> reservedQuantityByVariantId = new TreeMap<>();

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest itemRequest : request.items()) {
            var variant = variantsById.get(itemRequest.productVariantId());
            if (variant == null) {
                throw new ResourceNotFoundException("ProductVariant", "id", itemRequest.productVariantId());
            }

            int reservedQuantity = reservedQuantityByVariantId.getOrDefault(variant.id(), 0) + itemRequest.quantity();
            if (variant.stock() < reservedQuantity) {
                throw new BusinessException("INSUFFICIENT_STOCK", "Insufficient stock for SKU: " + variant.sku());
            }

            reservedQuantityByVariantId.put(variant.id(), reservedQuantity);

            var item = new OrderItem();
            item.setOrder(order);
            item.setProductVariantId(variant.id());
            item.setProductName(variant.productName());
            item.setVariantName(variant.name());
            item.setSku(variant.sku());
            item.setQuantity(itemRequest.quantity());
            item.setUnitPriceAmount(variant.priceAmount());
            item.setUnitPriceCurrency(variant.priceCurrency());
            item.setTotalAmount(variant.priceAmount().multiply(BigDecimal.valueOf(itemRequest.quantity())));
            order.getItems().add(item);
            total = total.add(item.getTotalAmount());
        }

        for (var entry : reservedQuantityByVariantId.entrySet()) {
            try {
                productVariantGateway.reserveStock(entry.getKey(), entry.getValue());
            } catch (BusinessException ex) {
                if ("INSUFFICIENT_STOCK".equals(ex.getErrorCode())) {
                    var variant = variantsById.get(entry.getKey());
                    throw new BusinessException("INSUFFICIENT_STOCK", "Insufficient stock for SKU: " + variant.sku());
                }
                throw ex;
            }
        }
        order.setTotalAmount(total);

        var saved = orderRepository.save(order);
        addStatusHistory(saved, OrderStatus.PENDING, "Order created");
        publishEvent("order.created", orderMapper.toDto(saved));
        log.info("Order created: orderId={}, userId={}, itemsCount={}, totalAmount={}",
            saved.getId(),
            userId,
            saved.getItems().size(),
            saved.getTotalAmount());
        return orderMapper.toDto(saved);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public OrderDTO updateStatus(Long orderId, OrderEvent event, String comment) {
        var order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        var previousStatus = order.getStatus();
        var newStatus = stateMachineConfig.apply(previousStatus, event);
        order.setStatus(newStatus);
        addStatusHistory(order, newStatus, comment);
        var saved = orderRepository.save(order);
        publishEvent("order.status-changed", orderMapper.toDto(saved));
        log.info("Order status updated: orderId={}, from={}, to={}, event={}",
            orderId,
            previousStatus,
            newStatus,
            event);
        return orderMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderDTO> getUserOrders(Long userId, Pageable pageable) {
        var page = orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.of(page.map(orderMapper::toDto));
    }

    @Transactional(readOnly = true)
    public OrderDTO getOrder(Long orderId, Long userId) {
        var order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("ACCESS_DENIED", "Order belongs to another user");
        }
        return orderMapper.toDto(order);
    }

    private void addStatusHistory(Order order, OrderStatus status, String comment) {
        var history = new OrderStatusHistory();
        history.setOrder(order);
        history.setStatus(status);
        history.setComment(comment);
        order.getStatusHistory().add(history);
    }

    private void publishEvent(String routingKey, OrderDTO orderDto) {
        outboxService.queueEvent(RabbitMQConfig.ORDER_EXCHANGE, routingKey, orderDto);
    }
}
