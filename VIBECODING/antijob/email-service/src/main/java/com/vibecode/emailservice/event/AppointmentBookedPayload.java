package com.vibecode.emailservice.event;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

// Payload tự định nghĩa, khớp JSON booking-service publish — không share jar với producer
// (mỗi service tự sở hữu bản sao của mình, đúng quyết định kiến trúc đã chốt).
@Data
@NoArgsConstructor
public class AppointmentBookedPayload {
    private Long appointmentId;
    private String patientEmail;
    private String patientName;
    private String dentistName;
    private String serviceName;
    private LocalDate appointmentDate;
    private LocalTime startTime;
}
