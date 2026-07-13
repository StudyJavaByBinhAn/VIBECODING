package com.vibecode.antijob.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DentistResponse {
    private Long id;
    private String fullName;
    private String phone;
    private String specialization;
    private String licenseNumber;
    private String bio;
    private boolean active;
}
