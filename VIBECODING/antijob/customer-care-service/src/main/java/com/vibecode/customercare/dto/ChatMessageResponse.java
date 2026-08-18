package com.vibecode.customercare.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ChatMessageResponse {
    private String id;
    private String conversationId;
    private String senderEmail;
    private String senderRole;
    private String content;
    private Instant sentAt;
}
