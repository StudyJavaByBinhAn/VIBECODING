package com.vibecode.antijob.dto;

import com.vibecode.antijob.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterStaffRequest {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 6)
    private String password;

    @NotBlank
    private String fullName;

    @NotBlank
    private String phone;

    @NotNull
    private Role role;

    // Chỉ áp dụng khi role = DENTIST
    private String specialization;
    private String licenseNumber;
}
