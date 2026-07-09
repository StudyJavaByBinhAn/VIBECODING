package com.vibecode.antijob.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class AppointmentRequest {
    // nullable có chủ đích — null nghĩa là auto-assign dentist (Phase 6)
    private Long dentistId;

    @NotNull
    private Long serviceId;

    @NotNull
    private LocalDate appointmentDate;

    @NotNull
    private LocalTime startTime;

    @Size(max = 500)
    private String notes;
}
