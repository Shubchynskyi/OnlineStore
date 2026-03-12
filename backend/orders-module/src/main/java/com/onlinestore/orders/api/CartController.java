package com.onlinestore.orders.api;

import com.onlinestore.common.security.AuthenticatedUserResolver;
import com.onlinestore.orders.dto.AddCartItemRequest;
import com.onlinestore.orders.dto.CartDTO;
import com.onlinestore.orders.dto.UpdateCartItemQuantityRequest;
import com.onlinestore.orders.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final CartService cartService;

    @GetMapping
    public CartDTO getCart(@AuthenticationPrincipal Jwt jwt) {
        return cartService.getCart(authenticatedUserResolver.resolve(jwt).requiredUserId());
    }

    @PostMapping("/items")
    public CartDTO addItem(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(authenticatedUserResolver.resolve(jwt).requiredUserId(), request);
    }

    @PatchMapping("/items/{id}")
    public CartDTO updateItemQuantity(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id,
        @Valid @RequestBody UpdateCartItemQuantityRequest request
    ) {
        return cartService.updateItemQuantity(authenticatedUserResolver.resolve(jwt).requiredUserId(), id, request);
    }

    @DeleteMapping("/items/{id}")
    public CartDTO removeItem(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return cartService.removeItem(authenticatedUserResolver.resolve(jwt).requiredUserId(), id);
    }
}
