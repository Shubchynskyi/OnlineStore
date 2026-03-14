package com.onlinestore.orders.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.onlinestore.orders.entity.Cart;
import com.onlinestore.orders.entity.CartItem;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CartMapperTest {

    private final CartMapper cartMapper = new CartMapper();

    @Test
    void toDtoShouldMapCartItems() {
        var cart = new Cart();
        cart.setTotalAmount(new BigDecimal("59.98"));
        cart.setTotalCurrency("EUR");

        var item = new CartItem();
        item.setId(8L);
        item.setCart(cart);
        item.setProductVariantId(101L);
        item.setProductName("Sneakers");
        item.setVariantName("42");
        item.setSku("SNK-42");
        item.setQuantity(2);
        item.setUnitPriceAmount(new BigDecimal("29.99"));
        item.setUnitPriceCurrency("EUR");
        item.setTotalAmount(new BigDecimal("59.98"));
        cart.setItems(List.of(item));

        var dto = cartMapper.toDto(cart);

        assertThat(dto.totalAmount()).isEqualByComparingTo("59.98");
        assertThat(dto.totalCurrency()).isEqualTo("EUR");
        assertThat(dto.items()).hasSize(1);
        assertThat(dto.items().get(0).id()).isEqualTo(8L);
        assertThat(dto.items().get(0).sku()).isEqualTo("SNK-42");
        assertThat(dto.items().get(0).totalAmount()).isEqualByComparingTo("59.98");
    }

    @Test
    void emptyShouldReturnStableDefaultCurrency() {
        var dto = cartMapper.empty();

        assertThat(dto.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.totalCurrency()).isEqualTo(Cart.DEFAULT_CURRENCY);
        assertThat(dto.items()).isEmpty();
    }
}
