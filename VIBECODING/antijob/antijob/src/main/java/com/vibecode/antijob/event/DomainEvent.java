package com.vibecode.antijob.event;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

// Envelope chung cho mọi event publish lên Kafka — consumer (email-service, sau này
// customer-care-service) tự định nghĩa payload DTO khớp JSON, không share jar với producer.
@Data
@Builder
public class DomainEvent<T> {
    private String eventId;
    private String eventType;
    private int version;
    private Instant occurredAt;
    private T data;
}
