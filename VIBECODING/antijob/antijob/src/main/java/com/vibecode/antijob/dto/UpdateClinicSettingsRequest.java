package com.vibecode.antijob.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.time.LocalTime;

@Data
public class UpdateClinicSettingsRequest {
    @NotBlank
    private String clinicName;

    @NotNull
    private LocalTime openTime;

    @NotNull
    private LocalTime closeTime;

    @Positive
    private int slotDurationMinutes;

    @Positive
    private int maxAdvanceBookingDays;

    @PositiveOrZero
    private int bufferMinutes;

    // Nullable có chủ đích — null nghĩa là clinic không nghỉ trưa
    private LocalTime breakStart;
    private LocalTime breakEnd;

    @PositiveOrZero
    private int cancelBeforeHours;

    @Positive
    private int maxPendingAppointments;
}
