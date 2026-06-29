package com.vibecode.antijob.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DentalService {
    private Long id;
    private String name;
    private String description;
    private int durationMinutes;
    private BigDecimal price;
    private boolean active;
}
