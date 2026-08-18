package com.vibecode.customercare.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

// customer-care-service không tự phát hành token (không có generateToken), nên test tự dựng
// JWT thủ công bằng jjwt trực tiếp, mô phỏng đúng token thật booking-service sẽ ký.
class JwtVerifierTest {

    private static final String SECRET = "dGhpcy1pcy1hLWRldi1vbmx5LXNlY3JldC1rZXktY2hhbmdlLWluLXByb2Q=";

    private final JwtVerifier jwtVerifier = new JwtVerifier(SECRET);

    private String buildToken(String email, String role, long expiryOffsetMs) {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiryOffsetMs))
                .signWith(key)
                .compact();
    }

    @Test
    void extractEmailAndRole_matchTokenClaims() {
        String token = buildToken("patient@dental.vn", "PATIENT", 60_000);

        assertThat(jwtVerifier.extractEmail(token)).isEqualTo("patient@dental.vn");
        assertThat(jwtVerifier.extractRole(token)).isEqualTo("PATIENT");
    }

    @Test
    void isValid_forFreshToken_returnsTrue() {
        String token = buildToken("dentist@dental.vn", "DENTIST", 60_000);

        assertThat(jwtVerifier.isValid(token)).isTrue();
    }

    @Test
    void isValid_forExpiredToken_returnsFalse() {
        String expired = buildToken("patient@dental.vn", "PATIENT", -1000);

        assertThat(jwtVerifier.isValid(expired)).isFalse();
    }

    @Test
    void isValid_forTokenSignedWithDifferentSecret_returnsFalse() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                Base64.getDecoder().decode("YW5vdGhlci1kaWZmZXJlbnQtc2VjcmV0LWtleS1mb3ItdGVzdGluZy1vbmx5"));
        String token = Jwts.builder().subject("x@y.com").claim("role", "PATIENT")
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey).compact();

        assertThat(jwtVerifier.isValid(token)).isFalse();
    }

    @Test
    void isValid_forMalformedToken_returnsFalse() {
        assertThat(jwtVerifier.isValid("not-a-jwt")).isFalse();
    }
}
