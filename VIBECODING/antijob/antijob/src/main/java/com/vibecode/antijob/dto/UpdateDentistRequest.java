package com.vibecode.antijob.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateDentistRequest {
    @NotBlank
    private String fullName;

    @NotBlank
    private String phone;

    private String specialization;

    @NotBlank
    private String licenseNumber;

    private String bio;
}
