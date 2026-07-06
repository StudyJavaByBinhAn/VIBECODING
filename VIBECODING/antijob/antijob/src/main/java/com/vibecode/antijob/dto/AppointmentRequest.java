package com.vibecode.antijob.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class AppointmentRequest {
    private Long dentistId;
    private Long serviceId;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private String notes;
}
