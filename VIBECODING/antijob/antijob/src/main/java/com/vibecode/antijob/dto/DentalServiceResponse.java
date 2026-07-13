package com.vibecode.antijob.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DentalServiceResponse {
    private Long id;
    private String name;
    private String description;
    private int durationMinutes;
    private BigDecimal price;
    private boolean active;
}
