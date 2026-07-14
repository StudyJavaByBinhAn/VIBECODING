package com.vibecode.antijob.service;

import com.vibecode.antijob.dto.AuthResponse;
import com.vibecode.antijob.dto.ChangePasswordRequest;
import com.vibecode.antijob.dto.LoginRequest;
import com.vibecode.antijob.dto.RegisterRequest;
import com.vibecode.antijob.dto.RegisterStaffRequest;
import com.vibecode.antijob.entity.Dentist;
import com.vibecode.antijob.entity.Patient;
import com.vibecode.antijob.entity.User;
import com.vibecode.antijob.enums.Role;
import com.vibecode.antijob.repository.DentistRepository;
import com.vibecode.antijob.repository.PatientRepository;
import com.vibecode.antijob.repository.UserRepository;
import com.vibecode.antijob.security.JwtUtil;
import com.vibecode.antijob.security.TokenRevocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int RESET_TOKEN_VALID_MINUTES = 30;

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DentistRepository dentistRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final TokenRevocationService tokenRevocationService;
    private final Clock clock;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email đã được sử dụng: " + req.getEmail());
        }

        User user = userRepository.save(User.builder()
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .role(Role.PATIENT)
                .build());

        patientRepository.save(Patient.builder()
                .user(user)
                .fullName(req.getFullName())
                .phone(req.getPhone())
                .build());

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse registerStaff(RegisterStaffRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email đã được sử dụng: " + req.getEmail());
        }
        if (req.getRole() == null) {
            throw new IllegalArgumentException("Phải chỉ định role");
        }

        User user = userRepository.save(User.builder()
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .role(req.getRole())
                .build());

        switch (req.getRole()) {
            case PATIENT -> patientRepository.save(Patient.builder()
                    .user(user)
                    .fullName(req.getFullName())
                    .phone(req.getPhone())
                    .build());
            case DENTIST -> dentistRepository.save(Dentist.builder()
                    .user(user)
                    .fullName(req.getFullName())
                    .phone(req.getPhone())
                    .specialization(req.getSpecialization())
                    .licenseNumber(req.getLicenseNumber())
                    .active(true)
                    .build());
            case ADMIN, RECEPTIONIST -> {
                // Chỉ cần User, không có entity hồ sơ riêng
            }
        }

        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Email hoặc mật khẩu không đúng"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Email hoặc mật khẩu không đúng");
        }

        return buildAuthResponse(user);
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest req) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản"));

        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không đúng");
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
        tokenRevocationService.revokeAllTokens(email);
    }

    public void logout(String email) {
        tokenRevocationService.revokeAllTokens(email);
    }

    @Transactional
    public void forgotPassword(String email) {
        // Không throw dù email không tồn tại — tránh lộ thông tin tài khoản nào đã đăng ký (account enumeration)
        userRepository.findByEmail(email).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            user.setResetToken(token);
            user.setResetTokenExpiry(LocalDateTime.now(clock).plusMinutes(RESET_TOKEN_VALID_MINUTES));
            userRepository.save(user);
            emailService.send(email, "Đặt lại mật khẩu",
                    "Mã đặt lại mật khẩu của bạn là: " + token + "\n"
                            + "Mã có hiệu lực trong " + RESET_TOKEN_VALID_MINUTES + " phút. "
                            + "Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này.");
            log.info("Đã gửi email đặt lại mật khẩu cho {}", email);
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetToken(token)
                .filter(u -> u.getResetTokenExpiry() != null && u.getResetTokenExpiry().isAfter(LocalDateTime.now(clock)))
                .orElseThrow(() -> new IllegalArgumentException("Token không hợp lệ hoặc đã hết hạn"));

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
        tokenRevocationService.revokeAllTokens(user.getEmail());
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        return AuthResponse.builder()
                .token(token)
                .role(user.getRole())
                .expiresIn(jwtUtil.getExpirationMs())
                .build();
    }
}
