package com.onlinestore.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.onlinestore.catalog.dto.ProductDTO;
import com.onlinestore.catalog.entity.ProductStatus;
import com.onlinestore.orders.dto.OrderDTO;
import com.onlinestore.orders.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class RealtimeMessageBroadcasterTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private RealtimeMessageBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new RealtimeMessageBroadcaster(messagingTemplate);
    }

    @Test
    void broadcastProductUpdateShouldTargetProductTopic() {
        var product = new ProductDTO(
            10L,
            "Phone",
            "phone",
            "Flagship phone",
            4L,
            "Electronics",
            "electronics",
            ProductStatus.ACTIVE,
            false,
            List.of(),
            List.of(),
            List.of()
        );
        var payloadCaptor = ArgumentCaptor.forClass(Object.class);

        broadcaster.broadcastProductUpdate(product, "product.updated");

        verify(messagingTemplate).convertAndSend(eq("/topic/products/10"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).isInstanceOf(RealtimeMessageBroadcaster.ProductRealtimeMessage.class);
        var payload = (RealtimeMessageBroadcaster.ProductRealtimeMessage) payloadCaptor.getValue();
        assertThat(payload.productId()).isEqualTo(10L);
        assertThat(payload.eventType()).isEqualTo("product.updated");
        assertThat(payload.slug()).isEqualTo("phone");
        assertThat(payload.name()).isEqualTo("Phone");
        assertThat(payload.status()).isEqualTo("ACTIVE");
    }

    @Test
    void broadcastOrderUpdateShouldTargetOrderTopic() {
        var order = new OrderDTO(
            25L,
            7L,
            OrderStatus.PAID,
            new BigDecimal("45.00"),
            "EUR",
            List.of(),
            Instant.parse("2026-03-13T12:00:00Z")
        );
        var payloadCaptor = ArgumentCaptor.forClass(Object.class);

        broadcaster.broadcastOrderUpdate(order, "order.status-changed");

        verify(messagingTemplate).convertAndSend(eq("/topic/orders/25"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).isInstanceOf(RealtimeMessageBroadcaster.OrderRealtimeMessage.class);
        var payload = (RealtimeMessageBroadcaster.OrderRealtimeMessage) payloadCaptor.getValue();
        assertThat(payload.orderId()).isEqualTo(25L);
        assertThat(payload.eventType()).isEqualTo("order.status-changed");
        assertThat(payload.status()).isEqualTo("PAID");
    }
}
