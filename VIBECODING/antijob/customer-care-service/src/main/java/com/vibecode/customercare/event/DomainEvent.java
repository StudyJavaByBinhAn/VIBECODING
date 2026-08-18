package com.vibecode.customercare.event;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

// Cùng envelope shape với booking-service (không share jar — bản copy riêng, đúng quyết định
// kiến trúc "3 service độc lập hoàn toàn").
@Data
@Builder
public class DomainEvent<T> {
    private String eventId;
    private String eventType;
    private int version;
    private Instant occurredAt;
    private T data;
}
