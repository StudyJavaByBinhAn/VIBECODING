package com.vibecode.emailservice.event;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
public class AppointmentCancelledPayload {
    private Long appointmentId;
    private String patientEmail;
    private String dentistName;
    private LocalDate appointmentDate;
    private LocalTime startTime;
}
