package com.vibecode.antijob.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicSettings {
    private Long id;
    private String clinicName;
    private LocalTime openTime;
    private LocalTime closeTime;
    private int slotDurationMinutes;
    private int maxAdvanceBookingDays;
}
