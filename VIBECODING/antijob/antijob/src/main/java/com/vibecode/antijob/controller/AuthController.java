package com.vibecode.antijob.controller;

import com.vibecode.antijob.dto.AuthResponse;
import com.vibecode.antijob.dto.LoginRequest;
import com.vibecode.antijob.dto.RegisterRequest;
import com.vibecode.antijob.dto.RegisterStaffRequest;
import com.vibecode.antijob.exception.ApiResponse;
import com.vibecode.antijob.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Đăng ký thành công", authService.register(req)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập thành công", authService.login(req)));
    }

    @PostMapping("/register-staff")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AuthResponse>> registerStaff(@Valid @RequestBody RegisterStaffRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Tạo tài khoản thành công", authService.registerStaff(req)));
    }
}
