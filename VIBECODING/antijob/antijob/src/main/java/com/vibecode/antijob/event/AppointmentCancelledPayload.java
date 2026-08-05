package com.vibecode.antijob.event;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
public class AppointmentCancelledPayload {
    private Long appointmentId;
    private String patientEmail;
    private String dentistName;
    private LocalDate appointmentDate;
    private LocalTime startTime;
}
