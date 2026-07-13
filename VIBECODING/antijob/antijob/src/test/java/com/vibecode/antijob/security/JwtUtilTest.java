package com.vibecode.antijob.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String SECRET = "dGhpcy1pcy1hLWRldi1vbmx5LXNlY3JldC1rZXktY2hhbmdlLWluLXByb2Q=";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, 86_400_000L);
    }

    @Test
    void generateToken_thenExtractEmailAndRole_matchInput() {
        String token = jwtUtil.generateToken("patient@dental.vn", "PATIENT");

        assertThat(jwtUtil.extractEmail(token)).isEqualTo("patient@dental.vn");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("PATIENT");
    }

    @Test
    void isValid_forFreshlyGeneratedToken_returnsTrue() {
        String token = jwtUtil.generateToken("admin@vibecode.local", "ADMIN");

        assertThat(jwtUtil.isValid(token)).isTrue();
    }

    @Test
    void isValid_forExpiredToken_returnsFalse() {
        JwtUtil shortLivedUtil = new JwtUtil(SECRET, -1000L);
        String expiredToken = shortLivedUtil.generateToken("patient@dental.vn", "PATIENT");

        assertThat(jwtUtil.isValid(expiredToken)).isFalse();
    }

    @Test
    void isValid_forMalformedToken_returnsFalse() {
        assertThat(jwtUtil.isValid("not-a-jwt-token")).isFalse();
    }

    @Test
    void isValid_forTokenSignedWithDifferentSecret_returnsFalse() {
        JwtUtil otherUtil = new JwtUtil("YW5vdGhlci1kaWZmZXJlbnQtc2VjcmV0LWtleS1mb3ItdGVzdGluZy1vbmx5", 86_400_000L);
        String tokenFromOtherSecret = otherUtil.generateToken("patient@dental.vn", "PATIENT");

        assertThat(jwtUtil.isValid(tokenFromOtherSecret)).isFalse();
    }
}
