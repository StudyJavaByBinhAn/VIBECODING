package com.vibecode.antijob.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateDentalServiceRequest {
    @NotBlank
    private String name;

    private String description;

    @Positive
    private int durationMinutes;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal price;
}
