package com.vibecode.antijob.service;

import com.vibecode.antijob.dto.AuthResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DentistRepository dentistRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

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

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        return AuthResponse.builder()
                .token(token)
                .role(user.getRole())
                .expiresIn(jwtUtil.getExpirationMs())
                .build();
    }
}
