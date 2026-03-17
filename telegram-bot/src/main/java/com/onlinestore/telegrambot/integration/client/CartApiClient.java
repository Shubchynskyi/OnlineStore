package com.onlinestore.telegrambot.integration.client;

import com.onlinestore.telegrambot.integration.dto.cart.AddCartItemRequest;
import com.onlinestore.telegrambot.integration.dto.cart.CartDto;
import com.onlinestore.telegrambot.integration.dto.cart.UpdateCartItemQuantityRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class CartApiClient {

    private final RestClient backendApiRestClient;
    private final BackendApiClientSupport backendApiClientSupport;

    public CartDto getCart(String accessToken) {
        return backendApiClientSupport.execute("cart.getCart", () -> backendApiRestClient.get()
            .uri("/api/v1/cart")
            .headers(headers -> headers.setBearerAuth(accessToken))
            .retrieve()
            .body(CartDto.class));
    }

    public CartDto addItem(String accessToken, AddCartItemRequest request) {
        return backendApiClientSupport.executeWithoutRetry("cart.addItem", () -> backendApiRestClient.post()
            .uri("/api/v1/cart/items")
            .headers(headers -> headers.setBearerAuth(accessToken))
            .body(request)
            .retrieve()
            .body(CartDto.class));
    }

    public CartDto updateItemQuantity(String accessToken, Long itemId, UpdateCartItemQuantityRequest request) {
        return backendApiClientSupport.executeWithoutRetry("cart.updateItemQuantity", () -> backendApiRestClient.patch()
            .uri("/api/v1/cart/items/{id}", itemId)
            .headers(headers -> headers.setBearerAuth(accessToken))
            .body(request)
            .retrieve()
            .body(CartDto.class));
    }

    public CartDto removeItem(String accessToken, Long itemId) {
        return backendApiClientSupport.executeWithoutRetry("cart.removeItem", () -> backendApiRestClient.delete()
            .uri("/api/v1/cart/items/{id}", itemId)
            .headers(headers -> headers.setBearerAuth(accessToken))
            .retrieve()
            .body(CartDto.class));
    }
}
