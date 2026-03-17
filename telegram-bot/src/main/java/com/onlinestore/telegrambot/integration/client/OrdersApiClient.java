package com.onlinestore.telegrambot.integration.client;

import com.onlinestore.telegrambot.integration.dto.PageResponse;
import com.onlinestore.telegrambot.integration.dto.orders.CreateOrderRequest;
import com.onlinestore.telegrambot.integration.dto.orders.OrderDto;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class OrdersApiClient {

    private static final ParameterizedTypeReference<PageResponse<OrderDto>> ORDERS_PAGE_TYPE =
        new ParameterizedTypeReference<>() {
        };

    private final RestClient backendApiRestClient;
    private final BackendApiClientSupport backendApiClientSupport;

    public OrderDto createOrder(String accessToken, CreateOrderRequest request) {
        return backendApiClientSupport.executeWithoutRetry("orders.createOrder", () -> backendApiRestClient.post()
            .uri("/api/v1/orders")
            .headers(headers -> headers.setBearerAuth(accessToken))
            .body(request)
            .retrieve()
            .body(OrderDto.class));
    }

    public PageResponse<OrderDto> getOrders(String accessToken, int page, int size) {
        return backendApiClientSupport.execute("orders.getOrders", () -> backendApiRestClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/v1/orders")
                .queryParam("page", page)
                .queryParam("size", size)
                .build())
            .headers(headers -> headers.setBearerAuth(accessToken))
            .retrieve()
            .body(ORDERS_PAGE_TYPE));
    }

    public OrderDto getOrder(String accessToken, Long orderId) {
        return backendApiClientSupport.execute("orders.getOrder", () -> backendApiRestClient.get()
            .uri("/api/v1/orders/{id}", orderId)
            .headers(headers -> headers.setBearerAuth(accessToken))
            .retrieve()
            .body(OrderDto.class));
    }

    public OrderDto confirmOrder(String comment, Long orderId) {
        return backendApiClientSupport.executeWithoutRetry("orders.confirmOrder", () -> backendApiRestClient.patch()
            .uri(uriBuilder -> uriBuilder
                .path("/api/admin/orders/{id}/status")
                .queryParam("event", "MANAGER_CONFIRM")
                .queryParamIfPresent("comment", Optional.ofNullable(comment).filter(StringUtils::hasText))
                .build(orderId))
            .headers(backendApiClientSupport::applyRequiredServiceAuthentication)
            .retrieve()
            .body(OrderDto.class));
    }
}
