package com.vibecode.customercare.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationResolverTest {

    @Mock
    private JwtVerifier jwtVerifier;
    @Mock
    private TokenRevocationCheck tokenRevocationCheck;

    private JwtAuthenticationResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new JwtAuthenticationResolver(jwtVerifier, tokenRevocationCheck);
    }

    @Test
    void resolve_validNonRevokedToken_returnsAuthenticationWithRoleAuthority() {
        Date issuedAt = new Date();
        when(jwtVerifier.isValid("tok")).thenReturn(true);
        when(jwtVerifier.extractEmail("tok")).thenReturn("patient@dental.vn");
        when(jwtVerifier.extractIssuedAt("tok")).thenReturn(issuedAt);
        when(jwtVerifier.extractRole("tok")).thenReturn("PATIENT");
        when(tokenRevocationCheck.isRevoked("patient@dental.vn", issuedAt)).thenReturn(false);

        Authentication auth = resolver.resolve("tok");

        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("patient@dental.vn");
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_PATIENT");
    }

    @Test
    void resolve_invalidSignature_returnsNull() {
        when(jwtVerifier.isValid("bad-tok")).thenReturn(false);

        assertThat(resolver.resolve("bad-tok")).isNull();
    }

    @Test
    void resolve_nullToken_returnsNull() {
        assertThat(resolver.resolve(null)).isNull();
    }

    @Test
    void resolve_revokedToken_returnsNull() {
        Date issuedAt = new Date();
        when(jwtVerifier.isValid("tok")).thenReturn(true);
        when(jwtVerifier.extractEmail("tok")).thenReturn("patient@dental.vn");
        when(jwtVerifier.extractIssuedAt("tok")).thenReturn(issuedAt);
        when(tokenRevocationCheck.isRevoked("patient@dental.vn", issuedAt)).thenReturn(true);

        assertThat(resolver.resolve("tok")).isNull();
    }
}
