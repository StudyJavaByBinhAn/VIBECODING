package com.vibecode.antijob.event;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
public class AppointmentBookedPayload {
    private Long appointmentId;
    private String patientEmail;
    private String patientName;
    private String dentistName;
    private String serviceName;
    private LocalDate appointmentDate;
    private LocalTime startTime;
}
