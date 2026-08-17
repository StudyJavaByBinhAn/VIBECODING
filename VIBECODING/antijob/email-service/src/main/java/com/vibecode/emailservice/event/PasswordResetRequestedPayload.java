package com.vibecode.emailservice.event;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class PasswordResetRequestedPayload {
    private String userEmail;
    private String resetToken;
    private LocalDateTime expiresAt;
}
