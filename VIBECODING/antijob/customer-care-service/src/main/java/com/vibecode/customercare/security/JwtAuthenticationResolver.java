package com.vibecode.customercare.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

// Dùng chung cho cả JwtAuthFilter (REST) lẫn StompAuthChannelInterceptor (WebSocket CONNECT) —
// tránh lặp lại 3 bước verify/revoke-check/build-Authentication ở 2 nơi. Không cần
// UserDetailsService/DB lookup như booking-service, vì role đã có sẵn trong claim JWT — email
// (subject) dùng luôn làm principal.
@Component
@RequiredArgsConstructor
public class JwtAuthenticationResolver {

    private final JwtVerifier jwtVerifier;
    private final TokenRevocationCheck tokenRevocationCheck;

    public Authentication resolve(String token) {
        if (token == null || !jwtVerifier.isValid(token)) {
            return null;
        }
        String email = jwtVerifier.extractEmail(token);
        if (tokenRevocationCheck.isRevoked(email, jwtVerifier.extractIssuedAt(token))) {
            return null;
        }
        String role = jwtVerifier.extractRole(token);
        return new UsernamePasswordAuthenticationToken(email, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
