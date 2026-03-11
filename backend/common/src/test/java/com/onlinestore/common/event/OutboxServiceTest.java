package com.onlinestore.common.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        outboxService = new OutboxService(outboxEventRepository, new ObjectMapper());
    }

    @Test
    void queueEventShouldPersistPendingOutboxRecord() {
        when(outboxEventRepository.save(any(OutboxEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        outboxService.queueEvent("order.events", "order.created", Map.of("orderId", 100L));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent event = captor.getValue();
        assertEquals("order.events", event.getExchangeName());
        assertEquals("order.created", event.getRoutingKey());
        assertEquals("order.created", event.getEventType());
        assertEquals(OutboxEventStatus.PENDING, event.getStatus());
        assertEquals(0, event.getAttemptCount());
        assertNotNull(event.getNextAttemptAt());
        assertNotNull(event.getPayload());
    }

    @Test
    void queueEventShouldUseDomainEventTypeWhenPayloadImplementsDomainEvent() {
        when(outboxEventRepository.save(any(OutboxEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        var eventPayload = new TestDomainEvent(42L, Instant.now());

        outboxService.queueEvent("payment.events", "fallback.routing", eventPayload);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertEquals("payment.completed", captor.getValue().getEventType());
    }

    @Test
    void queueEventShouldRejectBlankExchangeName() {
        assertThrows(IllegalArgumentException.class, () ->
            outboxService.queueEvent(" ", "order.created", Map.of("orderId", 1L)));
    }

    private record TestDomainEvent(Long id, Instant occurredAt) implements DomainEvent {
        @Override
        public String eventType() {
            return "payment.completed";
        }
    }
}
