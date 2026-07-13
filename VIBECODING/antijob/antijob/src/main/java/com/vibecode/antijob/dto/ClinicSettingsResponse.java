package com.vibecode.antijob.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicSettingsResponse {
    private Long id;
    private String clinicName;
    private LocalTime openTime;
    private LocalTime closeTime;
    private int slotDurationMinutes;
    private int maxAdvanceBookingDays;
    private int bufferMinutes;
    private LocalTime breakStart;
    private LocalTime breakEnd;
    private int cancelBeforeHours;
    private int maxPendingAppointments;
}
