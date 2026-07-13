package com.vibecode.antijob.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

@Data
public class UpdateWorkScheduleRequest {
    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;

    private boolean active;
}
