package com.vibecode.customercare.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;

// Đọc thẳng cùng 1 Redis instance với booking-service (key token:notBefore:{email}, ghi bởi
// TokenRevocationService bên đó khi logout/đổi mật khẩu) — CHỈ đọc, không ghi. Đây là điểm
// coupling hạ tầng duy nhất có chủ đích giữa 2 service (xem plan file, phần "Cross-service auth")
// — không gọi network sang booking-service.
@Component
@RequiredArgsConstructor
public class TokenRevocationCheck {

    private static final String KEY_PREFIX = "token:notBefore:";

    private final StringRedisTemplate redisTemplate;

    public boolean isRevoked(String email, Date issuedAt) {
        String notBefore = redisTemplate.opsForValue().get(KEY_PREFIX + email);
        if (notBefore == null) {
            return false;
        }
        return issuedAt.getTime() < Long.parseLong(notBefore);
    }
}
