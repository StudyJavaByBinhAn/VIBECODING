package com.vibecode.antijob.event;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PasswordResetRequestedPayload {
    private String userEmail;
    private String resetToken;
    private LocalDateTime expiresAt;
}
