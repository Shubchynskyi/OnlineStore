package com.onlinestore.common.event;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void queueEvent(String exchangeName, String routingKey, Object payload) {
        if (exchangeName == null || exchangeName.isBlank()) {
            throw new IllegalArgumentException("exchangeName must not be blank");
        }
        if (routingKey == null || routingKey.isBlank()) {
            throw new IllegalArgumentException("routingKey must not be blank");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }

        var event = new OutboxEvent();
        event.setExchangeName(exchangeName);
        event.setRoutingKey(routingKey);
        event.setEventType(resolveEventType(routingKey, payload));
        event.setPayload(serializePayload(payload));
        event.setStatus(OutboxEventStatus.PENDING);
        event.setAttemptCount(0);
        event.setNextAttemptAt(Instant.now());

        outboxEventRepository.save(event);
    }

    private String serializePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize outbox payload", ex);
        }
    }

    private String resolveEventType(String routingKey, Object payload) {
        if (payload instanceof DomainEvent domainEvent && domainEvent.eventType() != null
            && !domainEvent.eventType().isBlank()) {
            return domainEvent.eventType();
        }
        return routingKey;
    }
}
