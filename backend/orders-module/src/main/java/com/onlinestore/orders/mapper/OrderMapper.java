package com.onlinestore.orders.mapper;

import com.onlinestore.orders.dto.OrderDTO;
import com.onlinestore.orders.dto.OrderItemDTO;
import com.onlinestore.orders.entity.Order;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    public OrderDTO toDto(Order order) {
        return new OrderDTO(
            order.getId(),
            order.getUserId(),
            order.getStatus(),
            order.getTotalAmount(),
            order.getTotalCurrency(),
            order.getItems().stream()
                .map(item -> new OrderItemDTO(
                    item.getProductVariantId(),
                    item.getProductName(),
                    item.getVariantName(),
                    item.getSku(),
                    item.getQuantity(),
                    item.getUnitPriceAmount(),
                    item.getUnitPriceCurrency(),
                    item.getTotalAmount()
                ))
                .toList(),
            order.getCreatedAt()
        );
    }
}
