package com.vibecode.customercare.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

// Bản copy-paste trimmed của JwtUtil bên booking-service — CHỈ phần verify, không có
// generateToken(). customer-care-service không phát hành token, chỉ đọc lại claim (email/role)
// đã có sẵn trong JWT do booking-service ký — quyết định kiến trúc: trùng lặp có kiểm soát
// thay vì tạo 1 Gradle module dùng chung (xem plan file, phần "Cross-service auth").
@Component
public class JwtVerifier {

    private final SecretKey key;

    public JwtVerifier(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(secret));
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public Date extractIssuedAt(String token) {
        return parseClaims(token).getIssuedAt();
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }
}
