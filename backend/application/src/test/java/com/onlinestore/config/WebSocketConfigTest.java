package com.onlinestore.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.onlinestore.common.port.orders.OrderAccessGateway;
import com.onlinestore.common.port.orders.OrderAccessView;
import com.onlinestore.common.security.AuthenticatedUser;
import com.onlinestore.common.security.AuthenticatedUserResolver;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class WebSocketConfigTest {

    @Mock
    private OrderAccessGateway orderAccessGateway;
    @Mock
    private AuthenticatedUserResolver authenticatedUserResolver;

    private WebSocketConfig webSocketConfig;

    @BeforeEach
    void setUp() {
        webSocketConfig = new WebSocketConfig(orderAccessGateway, authenticatedUserResolver);
    }

    @Test
    void authorizeSubscriptionShouldAllowOwnedOrderTopic() {
        Jwt jwt = jwt();
        StompHeaderAccessor accessor = subscribeAccessor("/topic/orders/25", jwt);
        when(authenticatedUserResolver.resolve(jwt)).thenReturn(new AuthenticatedUser(7L, "subject"));
        when(orderAccessGateway.findByIdAndUserId(25L, 7L))
            .thenReturn(Optional.of(new OrderAccessView(25L, 7L, BigDecimal.TEN, "EUR")));

        webSocketConfig.authorizeClientMessage(accessor);

        verify(orderAccessGateway).findByIdAndUserId(25L, 7L);
    }

    @Test
    void authorizeSubscriptionShouldRejectForeignOrderTopic() {
        Jwt jwt = jwt();
        StompHeaderAccessor accessor = subscribeAccessor("/topic/orders/25", jwt);
        when(authenticatedUserResolver.resolve(jwt)).thenReturn(new AuthenticatedUser(7L, "subject"));
        when(orderAccessGateway.findByIdAndUserId(25L, 7L)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> webSocketConfig.authorizeClientMessage(accessor));
    }

    @Test
    void authorizeSubscriptionShouldIgnorePublicProductTopic() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/products/10");

        webSocketConfig.authorizeClientMessage(accessor);

        verifyNoInteractions(orderAccessGateway, authenticatedUserResolver);
    }

    @Test
    void authorizeSubscriptionShouldRejectDirectSendsToBrokerTopics() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/topic/orders/25");

        assertThrows(AccessDeniedException.class, () -> webSocketConfig.authorizeClientMessage(accessor));
        verifyNoInteractions(orderAccessGateway, authenticatedUserResolver);
    }

    private Jwt jwt() {
        return Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "subject")
            .claim("user_id", 7L)
            .build();
    }

    private StompHeaderAccessor subscribeAccessor(String destination, Jwt jwt) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(new JwtAuthenticationToken(jwt));
        return accessor;
    }
}
