package com.onlinestore.orders.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.common.dto.PageResponse;
import com.onlinestore.common.security.AuthenticatedUser;
import com.onlinestore.common.security.AuthenticatedUserResolver;
import com.onlinestore.orders.dto.AddCartItemRequest;
import com.onlinestore.orders.dto.CartDTO;
import com.onlinestore.orders.dto.CreateOrderRequest;
import com.onlinestore.orders.dto.OrderDTO;
import com.onlinestore.orders.dto.UpdateCartItemQuantityRequest;
import com.onlinestore.orders.entity.OrderEvent;
import com.onlinestore.orders.service.CartService;
import com.onlinestore.orders.service.OrderService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class OrdersApiControllersTest {

    @Mock
    private AuthenticatedUserResolver authenticatedUserResolver;

    @Mock
    private OrderService orderService;

    @Mock
    private CartService cartService;

    private Jwt jwt;
    private AuthenticatedUser authenticatedUser;
    private OrderController orderController;
    private CartController cartController;

    @BeforeEach
    void setUp() {
        jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "kc-1")
            .build();
        authenticatedUser = new AuthenticatedUser(42L, "kc-1");
        orderController = new OrderController(authenticatedUserResolver, orderService);
        cartController = new CartController(authenticatedUserResolver, cartService);
    }

    @Test
    void createOrderShouldDelegateToOrderService() {
        stubAuthenticatedUser();
        var request = mock(CreateOrderRequest.class);
        var response = mock(OrderDTO.class);
        when(orderService.createOrder(42L, request)).thenReturn(response);

        assertThat(orderController.createOrder(jwt, request)).isSameAs(response);

        verify(orderService).createOrder(42L, request);
    }

    @Test
    void getOrdersShouldDelegateToOrderService() {
        stubAuthenticatedUser();
        var pageable = Pageable.unpaged();
        var response = new PageResponse<OrderDTO>(List.of(), 0, 20, 0, 0, true);
        when(orderService.getUserOrders(42L, pageable)).thenReturn(response);

        assertThat(orderController.getOrders(jwt, pageable)).isSameAs(response);

        verify(orderService).getUserOrders(42L, pageable);
    }

    @Test
    void getOrderShouldDelegateToOrderService() {
        stubAuthenticatedUser();
        var response = mock(OrderDTO.class);
        when(orderService.getOrder(15L, 42L)).thenReturn(response);

        assertThat(orderController.getOrder(jwt, 15L)).isSameAs(response);

        verify(orderService).getOrder(15L, 42L);
    }

    @Test
    void updateStatusShouldDelegateToOrderService() {
        var response = mock(OrderDTO.class);
        when(orderService.updateStatus(15L, OrderEvent.PAYMENT_RECEIVED, "approved")).thenReturn(response);

        assertThat(orderController.updateStatus(15L, OrderEvent.PAYMENT_RECEIVED, "approved")).isSameAs(response);

        verify(orderService).updateStatus(15L, OrderEvent.PAYMENT_RECEIVED, "approved");
    }

    @Test
    void getCartShouldDelegateToCartService() {
        stubAuthenticatedUser();
        var response = mock(CartDTO.class);
        when(cartService.getCart(42L)).thenReturn(response);

        assertThat(cartController.getCart(jwt)).isSameAs(response);

        verify(cartService).getCart(42L);
    }

    @Test
    void addItemShouldDelegateToCartService() {
        stubAuthenticatedUser();
        var request = mock(AddCartItemRequest.class);
        var response = mock(CartDTO.class);
        when(cartService.addItem(42L, request)).thenReturn(response);

        assertThat(cartController.addItem(jwt, request)).isSameAs(response);

        verify(cartService).addItem(42L, request);
    }

    @Test
    void updateItemQuantityShouldDelegateToCartService() {
        stubAuthenticatedUser();
        var request = mock(UpdateCartItemQuantityRequest.class);
        var response = mock(CartDTO.class);
        when(cartService.updateItemQuantity(42L, 8L, request)).thenReturn(response);

        assertThat(cartController.updateItemQuantity(jwt, 8L, request)).isSameAs(response);

        verify(cartService).updateItemQuantity(42L, 8L, request);
    }

    @Test
    void removeItemShouldDelegateToCartService() {
        stubAuthenticatedUser();
        var response = mock(CartDTO.class);
        when(cartService.removeItem(42L, 8L)).thenReturn(response);

        assertThat(cartController.removeItem(jwt, 8L)).isSameAs(response);

        verify(cartService).removeItem(42L, 8L);
    }

    private void stubAuthenticatedUser() {
        when(authenticatedUserResolver.resolve(jwt)).thenReturn(authenticatedUser);
    }
}
