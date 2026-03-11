package com.onlinestore.orders.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.onlinestore.orders.entity.Order;
import com.onlinestore.orders.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderAccessGatewayImplTest {

    @Mock
    private OrderRepository orderRepository;

    private OrderAccessGatewayImpl gateway;

    @BeforeEach
    void setUp() {
        gateway = new OrderAccessGatewayImpl(orderRepository);
    }

    @Test
    void findByIdAndUserIdShouldMapOrderAccessView() {
        var order = new Order();
        order.setId(15L);
        order.setUserId(7L);
        order.setTotalAmount(new BigDecimal("55.50"));
        order.setTotalCurrency("EUR");

        when(orderRepository.findByIdAndUserId(15L, 7L)).thenReturn(Optional.of(order));

        var result = gateway.findByIdAndUserId(15L, 7L);

        assertTrue(result.isPresent());
        assertEquals(15L, result.get().id());
        assertEquals(7L, result.get().userId());
        assertEquals(new BigDecimal("55.50"), result.get().totalAmount());
        assertEquals("EUR", result.get().totalCurrency());
    }
}
