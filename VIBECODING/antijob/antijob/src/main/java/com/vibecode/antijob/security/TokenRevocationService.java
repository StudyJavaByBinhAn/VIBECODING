package com.vibecode.antijob.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;

/**
 * JWT là stateless nên không thể "xoá" token đã phát hành — thay vào đó lưu mốc thời gian
 * "notBefore" theo email trong Redis: token có issuedAt trước mốc này bị coi là đã thu hồi.
 * Áp dụng cho logout (thu hồi mọi token đang có, mọi thiết bị) và đổi/đặt lại mật khẩu
 * (JwtAuthFilter sẽ dùng lại để chặn token cũ ngay sau khi mật khẩu đổi).
 */
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private static final String KEY_PREFIX = "token:notBefore:";

    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;

    public void revokeAllTokens(String email) {
        String key = KEY_PREFIX + email;
        redisTemplate.opsForValue().set(key, String.valueOf(System.currentTimeMillis()),
                Duration.ofMillis(jwtUtil.getExpirationMs()));
    }

    public boolean isRevoked(String email, Date issuedAt) {
        String notBefore = redisTemplate.opsForValue().get(KEY_PREFIX + email);
        if (notBefore == null) {
            return false;
        }
        return issuedAt.getTime() < Long.parseLong(notBefore);
    }
}
