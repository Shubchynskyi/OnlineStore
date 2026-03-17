package com.onlinestore.telegrambot.integration.service;

import com.onlinestore.telegrambot.integration.client.OrdersApiClient;
import com.onlinestore.telegrambot.integration.dto.orders.OrderDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ManagerOrdersIntegrationService {

    private final OrdersApiClient ordersApiClient;

    public OrderDto confirmOrder(Long orderId, String comment) {
        return ordersApiClient.confirmOrder(comment, orderId);
    }
}
