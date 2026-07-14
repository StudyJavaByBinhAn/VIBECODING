package com.vibecode.antijob.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenRevocationServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private JwtUtil jwtUtil;

    private TokenRevocationService service;

    @BeforeEach
    void setUp() {
        service = new TokenRevocationService(redisTemplate, jwtUtil);
    }

    @Test
    void revokeAllTokens_writesNotBeforeKeyWithTtlEqualToTokenExpiration() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(jwtUtil.getExpirationMs()).thenReturn(86_400_000L);

        service.revokeAllTokens("patient@dental.vn");

        verify(valueOperations).set(eq("token:notBefore:patient@dental.vn"), anyString(), eq(Duration.ofMillis(86_400_000L)));
    }

    @Test
    void isRevoked_noNotBeforeStored_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("token:notBefore:patient@dental.vn")).thenReturn(null);

        assertThat(service.isRevoked("patient@dental.vn", new Date())).isFalse();
    }

    @Test
    void isRevoked_tokenIssuedBeforeNotBefore_returnsTrue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        long notBefore = System.currentTimeMillis();
        when(valueOperations.get("token:notBefore:patient@dental.vn")).thenReturn(String.valueOf(notBefore));
        Date issuedAt = new Date(notBefore - 5000);

        assertThat(service.isRevoked("patient@dental.vn", issuedAt)).isTrue();
    }

    @Test
    void isRevoked_tokenIssuedAfterNotBefore_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        long notBefore = System.currentTimeMillis();
        when(valueOperations.get("token:notBefore:patient@dental.vn")).thenReturn(String.valueOf(notBefore));
        Date issuedAt = new Date(notBefore + 5000);

        assertThat(service.isRevoked("patient@dental.vn", issuedAt)).isFalse();
    }
}
