package com.onlinestore.common.event;

import java.time.Instant;

public interface DomainEvent {

    String eventType();

    Instant occurredAt();
}
