package com.vibecode.antijob.dto;

import com.vibecode.antijob.enums.Role;
import lombok.Data;

@Data
public class RegisterStaffRequest {
    private String email;
    private String password;
    private String fullName;
    private String phone;
    private Role role;

    // Chỉ áp dụng khi role = DENTIST
    private String specialization;
    private String licenseNumber;
}
