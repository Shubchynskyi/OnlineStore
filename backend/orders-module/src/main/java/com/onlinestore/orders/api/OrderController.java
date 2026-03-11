package com.onlinestore.orders.api;

import com.onlinestore.common.dto.PageResponse;
import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.orders.dto.CreateOrderRequest;
import com.onlinestore.orders.dto.OrderDTO;
import com.onlinestore.orders.entity.OrderEvent;
import com.onlinestore.orders.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/api/v1/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDTO createOrder(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CreateOrderRequest request
    ) {
        return orderService.createOrder(extractUserId(jwt), request);
    }

    @GetMapping("/api/v1/orders")
    public PageResponse<OrderDTO> getOrders(@AuthenticationPrincipal Jwt jwt, Pageable pageable) {
        return orderService.getUserOrders(extractUserId(jwt), pageable);
    }

    @GetMapping("/api/v1/orders/{id}")
    public OrderDTO getOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return orderService.getOrder(id, extractUserId(jwt));
    }

    @PatchMapping("/api/admin/orders/{id}/status")
    public OrderDTO updateStatus(
        @PathVariable Long id,
        @RequestParam OrderEvent event,
        @RequestParam(required = false) String comment
    ) {
        return orderService.updateStatus(id, event, comment);
    }

    private Long extractUserId(Jwt jwt) {
        String claimValue = jwt.getClaimAsString("user_id");
        if (claimValue == null || claimValue.isBlank()) {
            throw new BusinessException("INVALID_TOKEN", "user_id claim is missing");
        }
        try {
            return Long.parseLong(claimValue);
        } catch (NumberFormatException ex) {
            throw new BusinessException("INVALID_TOKEN", "user_id claim has invalid format");
        }
    }
}
