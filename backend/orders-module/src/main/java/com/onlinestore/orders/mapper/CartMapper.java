package com.onlinestore.orders.mapper;

import com.onlinestore.orders.dto.CartDTO;
import com.onlinestore.orders.dto.CartItemDTO;
import com.onlinestore.orders.entity.Cart;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CartMapper {

    public CartDTO toDto(Cart cart) {
        return new CartDTO(
            cart.getTotalAmount(),
            cart.getTotalCurrency(),
            cart.getItems().stream()
                .map(item -> new CartItemDTO(
                    item.getId(),
                    item.getProductVariantId(),
                    item.getProductName(),
                    item.getVariantName(),
                    item.getSku(),
                    item.getQuantity(),
                    item.getUnitPriceAmount(),
                    item.getUnitPriceCurrency(),
                    item.getTotalAmount()
                ))
                .toList()
        );
    }

    public CartDTO empty() {
        return new CartDTO(BigDecimal.ZERO, Cart.DEFAULT_CURRENCY, List.of());
    }
}
