package com.onlinestore.telegrambot.integration.service;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.BackendAuthenticationRequiredException;
import com.onlinestore.telegrambot.integration.client.OrdersApiClient;
import com.onlinestore.telegrambot.integration.dto.PageResponse;
import com.onlinestore.telegrambot.integration.dto.orders.CreateOrderRequest;
import com.onlinestore.telegrambot.integration.dto.orders.OrderDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrdersIntegrationService {

    private final OrdersApiClient ordersApiClient;
    private final CustomerAccessTokenResolver customerAccessTokenResolver;
    private final BotProperties botProperties;

    public OrderDto createOrder(Long telegramUserId, CreateOrderRequest request) {
        return ordersApiClient.createOrder(requireAccessToken(telegramUserId), request);
    }

    public PageResponse<OrderDto> getOrders(Long telegramUserId) {
        return ordersApiClient.getOrders(
            requireAccessToken(telegramUserId),
            0,
            botProperties.getBackendApi().getRecentOrdersPageSize()
        );
    }

    public OrderDto getOrder(Long telegramUserId, Long orderId) {
        return ordersApiClient.getOrder(requireAccessToken(telegramUserId), orderId);
    }

    public OrderDto lookupOrder(Long telegramUserId, String orderReference) {
        try {
            return getOrder(telegramUserId, Long.parseLong(orderReference.trim()));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Order lookup expects a numeric backend order id.", ex);
        }
    }

    private String requireAccessToken(Long telegramUserId) {
        return customerAccessTokenResolver.resolveAccessToken(telegramUserId)
            .orElseThrow(() -> new BackendAuthenticationRequiredException(
                "Order integration is active, but this Telegram user is not linked to a backend customer token yet."
            ));
    }
}
