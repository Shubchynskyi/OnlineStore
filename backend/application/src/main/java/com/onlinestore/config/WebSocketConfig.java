package com.onlinestore.config;

import com.onlinestore.common.port.orders.OrderAccessGateway;
import com.onlinestore.common.security.AuthenticatedUserResolver;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String TOPIC_PREFIX = "/topic/";
    private static final String ORDER_TOPIC_PREFIX = "/topic/orders/";

    private final OrderAccessGateway orderAccessGateway;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @Value("${onlinestore.security.cors.allowed-origins:http://localhost:3000,http://localhost:4200}")
    private String allowedOrigins;

    public WebSocketConfig(
        OrderAccessGateway orderAccessGateway,
        AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.orderAccessGateway = orderAccessGateway;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns(resolveAllowedOrigins().toArray(String[]::new))
            .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                authorizeClientMessage(accessor);
                return message;
            }
        });
    }

    void authorizeClientMessage(StompHeaderAccessor accessor) {
        if (accessor == null) {
            return;
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }

        if (accessor.getCommand() == StompCommand.SEND && destination.startsWith(TOPIC_PREFIX)) {
            throw new AccessDeniedException("Direct sends to broker topics are not allowed");
        }

        if (accessor.getCommand() != StompCommand.SUBSCRIBE || !destination.startsWith(ORDER_TOPIC_PREFIX)) {
            return;
        }

        Long orderId = parseOrderTopicId(destination);
        if (!(accessor.getUser() instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
            throw new AccessDeniedException("Order topic subscription requires JWT authentication");
        }

        Long userId = authenticatedUserResolver.resolve(jwtAuthenticationToken.getToken()).requiredUserId();
        if (orderAccessGateway.findByIdAndUserId(orderId, userId).isEmpty()) {
            throw new AccessDeniedException("Order topic subscription denied");
        }
    }

    private List<String> resolveAllowedOrigins() {
        return Stream.of(allowedOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isBlank())
            .toList();
    }

    private Long parseOrderTopicId(String destination) {
        String orderIdValue = destination.substring(ORDER_TOPIC_PREFIX.length());
        try {
            long orderId = Long.parseLong(orderIdValue);
            if (orderId <= 0) {
                throw new NumberFormatException("orderId must be positive");
            }
            return orderId;
        } catch (NumberFormatException ex) {
            throw new AccessDeniedException("Invalid order topic destination");
        }
    }
}
