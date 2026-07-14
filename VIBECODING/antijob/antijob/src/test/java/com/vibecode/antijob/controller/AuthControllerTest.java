package com.vibecode.antijob.controller;

import tools.jackson.databind.ObjectMapper;
import com.vibecode.antijob.config.MethodSecurityTestConfig;
import com.vibecode.antijob.config.SecurityConfig;
import com.vibecode.antijob.dto.AuthResponse;
import com.vibecode.antijob.dto.ChangePasswordRequest;
import com.vibecode.antijob.dto.ForgotPasswordRequest;
import com.vibecode.antijob.dto.LoginRequest;
import com.vibecode.antijob.dto.RegisterRequest;
import com.vibecode.antijob.dto.RegisterStaffRequest;
import com.vibecode.antijob.dto.ResetPasswordRequest;
import com.vibecode.antijob.enums.Role;
import com.vibecode.antijob.security.JwtAuthFilter;
import com.vibecode.antijob.security.RateLimitFilter;
import com.vibecode.antijob.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthFilter.class, RateLimitFilter.class}))
@Import(MethodSecurityTestConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private AuthService authService;

    @Test
    void register_validRequest_returns200() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("patient@dental.vn");
        req.setPassword("secret123");
        req.setFullName("Patient A");
        req.setPhone("0900000000");
        when(authService.register(any())).thenReturn(
                AuthResponse.builder().token("jwt-token").role(Role.PATIENT).expiresIn(86_400_000L).build());

        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("jwt-token"));
    }

    @Test
    void register_missingRequiredFields_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest();

        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_validRequest_returns200() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("patient@dental.vn");
        req.setPassword("secret123");
        when(authService.login(any())).thenReturn(
                AuthResponse.builder().token("jwt-token").role(Role.PATIENT).expiresIn(86_400_000L).build());

        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("PATIENT"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void registerStaff_asAdmin_returns200() throws Exception {
        RegisterStaffRequest req = new RegisterStaffRequest();
        req.setEmail("bs.new@dental.vn");
        req.setPassword("secret123");
        req.setFullName("BS Moi");
        req.setPhone("0900000001");
        req.setRole(Role.DENTIST);
        when(authService.registerStaff(any())).thenReturn(
                AuthResponse.builder().token("jwt-token").role(Role.DENTIST).expiresIn(86_400_000L).build());

        mockMvc.perform(post("/api/auth/register-staff").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void registerStaff_asPatient_returns403() throws Exception {
        RegisterStaffRequest req = new RegisterStaffRequest();
        req.setEmail("bs.new@dental.vn");
        req.setPassword("secret123");
        req.setFullName("BS Moi");
        req.setPhone("0900000001");
        req.setRole(Role.DENTIST);

        mockMvc.perform(post("/api/auth/register-staff").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void changePassword_authenticated_returns200() throws Exception {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("oldpass");
        req.setNewPassword("newpass123");
        var principal = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "patient@dental.vn", null);

        mockMvc.perform(patch("/api/auth/me/password").with(csrf())
                        .principal(principal)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(authService).changePassword("patient@dental.vn", req);
    }

    @Test
    @WithMockUser
    void forgotPassword_validEmail_returns200() throws Exception {
        ForgotPasswordRequest req = new ForgotPasswordRequest();
        req.setEmail("patient@dental.vn");

        mockMvc.perform(post("/api/auth/forgot-password").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void resetPassword_missingToken_returns400() throws Exception {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setNewPassword("newpass123");

        mockMvc.perform(post("/api/auth/reset-password").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logout_authenticated_returns200() throws Exception {
        var principal = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "patient@dental.vn", null);

        mockMvc.perform(post("/api/auth/logout").with(csrf())
                        .principal(principal))
                .andExpect(status().isOk());

        verify(authService).logout("patient@dental.vn");
    }
}
