package com.vibecode.customercare.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ConversationResponse {
    private String id;
    private String patientEmail;
    private Instant createdAt;
    private Instant lastMessageAt;
}
