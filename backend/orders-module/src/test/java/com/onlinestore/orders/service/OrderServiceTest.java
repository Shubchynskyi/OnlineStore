package com.onlinestore.orders.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.common.event.OutboxService;
import com.onlinestore.common.port.address.AddressAccessGateway;
import com.onlinestore.common.port.catalog.ProductVariantGateway;
import com.onlinestore.common.port.catalog.ProductVariantOrderView;
import com.onlinestore.orders.dto.CreateOrderRequest;
import com.onlinestore.orders.dto.OrderDTO;
import com.onlinestore.orders.dto.OrderItemRequest;
import com.onlinestore.orders.entity.Order;
import com.onlinestore.orders.entity.OrderStatus;
import com.onlinestore.orders.mapper.OrderMapper;
import com.onlinestore.orders.repository.OrderRepository;
import com.onlinestore.orders.statemachine.OrderStateMachineConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductVariantGateway productVariantGateway;
    @Mock
    private AddressAccessGateway addressAccessGateway;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderStateMachineConfig stateMachineConfig;
    @Mock
    private OutboxService outboxService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
            orderRepository,
            productVariantGateway,
            addressAccessGateway,
            orderMapper,
            stateMachineConfig,
            outboxService
        );
    }

    @Test
    void createOrderShouldRejectAddressThatDoesNotBelongToUser() {
        when(addressAccessGateway.isAddressOwnedByUser(10L, 1L)).thenReturn(false);
        var request = new CreateOrderRequest(
            10L,
            List.of(new OrderItemRequest(1000L, 1)),
            "Leave at door"
        );

        assertThrows(BusinessException.class, () -> orderService.createOrder(1L, request));

        verifyNoInteractions(productVariantGateway, orderRepository, orderMapper, outboxService);
    }

    @Test
    void createOrderShouldReserveStockAtomicallyForAggregatedQuantities() {
        when(addressAccessGateway.isAddressOwnedByUser(10L, 1L)).thenReturn(true);
        var request = new CreateOrderRequest(
            10L,
            List.of(new OrderItemRequest(1000L, 1), new OrderItemRequest(1000L, 2)),
            "Leave at door"
        );
        var variant = new ProductVariantOrderView(
            1000L,
            "SKU-1000",
            "Blue",
            "Phone",
            new BigDecimal("10.00"),
            "EUR",
            10
        );
        when(productVariantGateway.findByIds(List.of(1000L))).thenReturn(Map.of(1000L, variant));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            var order = invocation.getArgument(0, Order.class);
            order.setId(500L);
            return order;
        });
        var expectedDto = new OrderDTO(
            500L,
            1L,
            OrderStatus.PENDING,
            new BigDecimal("30.00"),
            "EUR",
            List.of(),
            Instant.now()
        );
        when(orderMapper.toDto(any(Order.class))).thenReturn(expectedDto);

        var result = orderService.createOrder(1L, request);

        assertSame(expectedDto, result);
        verify(productVariantGateway).reserveStock(1000L, 3);
        verify(orderRepository).save(any(Order.class));
        verify(outboxService).queueEvent(any(), any(), any());
    }

    @Test
    void createOrderShouldFailWhenConcurrentStockReservationFails() {
        when(addressAccessGateway.isAddressOwnedByUser(10L, 1L)).thenReturn(true);
        var request = new CreateOrderRequest(
            10L,
            List.of(new OrderItemRequest(1000L, 1), new OrderItemRequest(1000L, 2)),
            "Leave at door"
        );
        var variant = new ProductVariantOrderView(
            1000L,
            "SKU-1000",
            "Blue",
            "Phone",
            new BigDecimal("10.00"),
            "EUR",
            10
        );
        when(productVariantGateway.findByIds(List.of(1000L))).thenReturn(Map.of(1000L, variant));
        doThrow(new BusinessException("INSUFFICIENT_STOCK", "Insufficient stock for variant id: 1000"))
            .when(productVariantGateway)
            .reserveStock(1000L, 3);

        var ex = assertThrows(BusinessException.class, () -> orderService.createOrder(1L, request));

        assertEquals("INSUFFICIENT_STOCK", ex.getErrorCode());
        assertEquals("Insufficient stock for SKU: SKU-1000", ex.getMessage());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrderShouldReserveStockInDeterministicVariantIdOrder() {
        when(addressAccessGateway.isAddressOwnedByUser(10L, 1L)).thenReturn(true);
        var request = new CreateOrderRequest(
            10L,
            List.of(new OrderItemRequest(2000L, 1), new OrderItemRequest(1000L, 1)),
            "Leave at door"
        );
        var variant2000 = new ProductVariantOrderView(
            2000L,
            "SKU-2000",
            "Black",
            "Phone",
            new BigDecimal("10.00"),
            "EUR",
            10
        );
        var variant1000 = new ProductVariantOrderView(
            1000L,
            "SKU-1000",
            "Blue",
            "Phone",
            new BigDecimal("20.00"),
            "EUR",
            10
        );
        when(productVariantGateway.findByIds(List.of(2000L, 1000L)))
            .thenReturn(Map.of(2000L, variant2000, 1000L, variant1000));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            var order = invocation.getArgument(0, Order.class);
            order.setId(501L);
            return order;
        });
        when(orderMapper.toDto(any(Order.class))).thenReturn(new OrderDTO(
            501L,
            1L,
            OrderStatus.PENDING,
            new BigDecimal("30.00"),
            "EUR",
            List.of(),
            Instant.now()
        ));

        orderService.createOrder(1L, request);

        InOrder reservationOrder = inOrder(productVariantGateway);
        reservationOrder.verify(productVariantGateway).reserveStock(1000L, 1);
        reservationOrder.verify(productVariantGateway).reserveStock(2000L, 1);
    }
}
