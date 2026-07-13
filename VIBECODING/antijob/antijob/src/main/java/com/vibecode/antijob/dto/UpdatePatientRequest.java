package com.vibecode.antijob.dto;

import com.vibecode.antijob.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdatePatientRequest {
    @NotBlank
    private String phone;

    private LocalDate dateOfBirth;
    private Gender gender;
    private String address;

    @Size(max = 2000)
    private String medicalHistory;
}
