package com.vibecode.antijob.service;

import com.vibecode.antijob.dto.ChangePasswordRequest;
import com.vibecode.antijob.entity.User;
import com.vibecode.antijob.enums.Role;
import com.vibecode.antijob.repository.DentistRepository;
import com.vibecode.antijob.repository.PatientRepository;
import com.vibecode.antijob.repository.UserRepository;
import com.vibecode.antijob.security.JwtUtil;
import com.vibecode.antijob.security.TokenRevocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 12, 10, 0);
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
    private static final String EMAIL = "patient@example.com";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private DentistRepository dentistRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private EmailService emailService;
    @Mock
    private TokenRevocationService tokenRevocationService;

    private AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, patientRepository, dentistRepository, passwordEncoder, jwtUtil,
                emailService, tokenRevocationService, FIXED_CLOCK);
        user = User.builder().id(1L).email(EMAIL).password("hashed-old").role(Role.PATIENT).build();
    }

    // ---------- changePassword() ----------

    @Test
    void changePassword_correctCurrentPassword_updatesToNewHash() {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("old123");
        req.setNewPassword("new123456");

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old123", "hashed-old")).thenReturn(true);
        when(passwordEncoder.encode("new123456")).thenReturn("hashed-new");

        authService.changePassword(EMAIL, req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("hashed-new");
        verify(tokenRevocationService).revokeAllTokens(EMAIL);
    }

    @Test
    void changePassword_wrongCurrentPassword_throwsIllegalArgument() {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("wrong");
        req.setNewPassword("new123456");

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed-old")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(EMAIL, req))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
        verify(tokenRevocationService, never()).revokeAllTokens(anyString());
    }

    // ---------- logout() ----------

    @Test
    void logout_revokesAllTokensForEmail() {
        authService.logout(EMAIL);

        verify(tokenRevocationService).revokeAllTokens(EMAIL);
    }

    // ---------- forgotPassword() ----------

    @Test
    void forgotPassword_existingEmail_setsResetTokenAndExpiry() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        authService.forgotPassword(EMAIL);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getResetToken()).isNotBlank();
        assertThat(captor.getValue().getResetTokenExpiry()).isEqualTo(NOW.plusMinutes(30));
        verify(emailService).send(eq(EMAIL), anyString(), anyString());
    }

    @Test
    void forgotPassword_unknownEmail_doesNothing_doesNotThrow() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        authService.forgotPassword("unknown@example.com");

        verify(userRepository, never()).save(any());
        verify(emailService, never()).send(anyString(), anyString(), anyString());
    }

    // ---------- resetPassword() ----------

    @Test
    void resetPassword_validToken_updatesPasswordAndClearsToken() {
        user.setResetToken("valid-token");
        user.setResetTokenExpiry(NOW.plusMinutes(10));

        when(userRepository.findByResetToken("valid-token")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpass123")).thenReturn("hashed-new");

        authService.resetPassword("valid-token", "newpass123");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("hashed-new");
        assertThat(captor.getValue().getResetToken()).isNull();
        assertThat(captor.getValue().getResetTokenExpiry()).isNull();
        verify(tokenRevocationService).revokeAllTokens(EMAIL);
    }

    @Test
    void resetPassword_expiredToken_throwsIllegalArgument() {
        user.setResetToken("expired-token");
        user.setResetTokenExpiry(NOW.minusMinutes(1));

        when(userRepository.findByResetToken("expired-token")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.resetPassword("expired-token", "newpass123"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_unknownToken_throwsIllegalArgument() {
        when(userRepository.findByResetToken("bogus")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword("bogus", "newpass123"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }
}
