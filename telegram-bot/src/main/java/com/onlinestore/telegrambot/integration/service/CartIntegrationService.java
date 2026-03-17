package com.onlinestore.telegrambot.integration.service;

import com.onlinestore.telegrambot.integration.BackendAuthenticationRequiredException;
import com.onlinestore.telegrambot.integration.client.CartApiClient;
import com.onlinestore.telegrambot.integration.dto.cart.AddCartItemRequest;
import com.onlinestore.telegrambot.integration.dto.cart.CartDto;
import com.onlinestore.telegrambot.integration.dto.cart.UpdateCartItemQuantityRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CartIntegrationService {

    private final CartApiClient cartApiClient;
    private final CustomerAccessTokenResolver customerAccessTokenResolver;

    public CartDto getCart(Long telegramUserId) {
        return cartApiClient.getCart(requireAccessToken(telegramUserId));
    }

    public CartDto addItem(Long telegramUserId, AddCartItemRequest request) {
        return cartApiClient.addItem(requireAccessToken(telegramUserId), request);
    }

    public CartDto updateItemQuantity(Long telegramUserId, Long itemId, UpdateCartItemQuantityRequest request) {
        return cartApiClient.updateItemQuantity(requireAccessToken(telegramUserId), itemId, request);
    }

    public CartDto removeItem(Long telegramUserId, Long itemId) {
        return cartApiClient.removeItem(requireAccessToken(telegramUserId), itemId);
    }

    private String requireAccessToken(Long telegramUserId) {
        return customerAccessTokenResolver.resolveAccessToken(telegramUserId)
            .orElseThrow(() -> new BackendAuthenticationRequiredException(
                "Cart integration is active, but this Telegram user is not linked to a backend customer token yet."
            ));
    }
}
