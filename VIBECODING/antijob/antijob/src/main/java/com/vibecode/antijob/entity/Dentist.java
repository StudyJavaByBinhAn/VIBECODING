package com.vibecode.antijob.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Dentist {
    private Long id;
    private User user;
    private String fullName;
    private String phone;
    private String specialization;
    private String licenseNumber;
    private String bio;
    private boolean active;
}
