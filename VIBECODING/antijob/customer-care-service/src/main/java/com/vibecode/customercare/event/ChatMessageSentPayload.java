package com.vibecode.customercare.event;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ChatMessageSentPayload {
    private String conversationId;
    private String patientEmail;
    private String senderEmail;
    private String senderRole;
    private String content;
    private Instant sentAt;
}
