package com.onlinestore.orders.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.onlinestore.orders.entity.Order;
import com.onlinestore.orders.entity.OrderItem;
import com.onlinestore.orders.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderMapperTest {

    private final OrderMapper orderMapper = new OrderMapper();

    @Test
    void toDtoShouldMapOrderItemsAndTotals() {
        var order = new Order();
        order.setId(21L);
        order.setUserId(9L);
        order.setStatus(OrderStatus.PROCESSING);
        order.setTotalAmount(new BigDecimal("129.99"));
        order.setTotalCurrency("EUR");
        order.setCreatedAt(Instant.parse("2026-03-14T00:00:00Z"));

        var item = new OrderItem();
        item.setOrder(order);
        item.setProductVariantId(44L);
        item.setProductName("Phone");
        item.setVariantName("Black");
        item.setSku("PHONE-BLK");
        item.setQuantity(2);
        item.setUnitPriceAmount(new BigDecimal("64.995"));
        item.setUnitPriceCurrency("EUR");
        item.setTotalAmount(new BigDecimal("129.99"));
        order.setItems(List.of(item));

        var dto = orderMapper.toDto(order);

        assertThat(dto.id()).isEqualTo(21L);
        assertThat(dto.userId()).isEqualTo(9L);
        assertThat(dto.status()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(dto.totalAmount()).isEqualByComparingTo("129.99");
        assertThat(dto.totalCurrency()).isEqualTo("EUR");
        assertThat(dto.createdAt()).isEqualTo(Instant.parse("2026-03-14T00:00:00Z"));
        assertThat(dto.items()).hasSize(1);
        assertThat(dto.items().get(0).productVariantId()).isEqualTo(44L);
        assertThat(dto.items().get(0).sku()).isEqualTo("PHONE-BLK");
        assertThat(dto.items().get(0).quantity()).isEqualTo(2);
    }
}
