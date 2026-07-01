package com.vibecode.antijob.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "dental_services")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DentalService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;

    @Column(name = "duration_minutes")
    private int durationMinutes;

    private BigDecimal price;

    @Column(name = "is_active")
    private boolean active;
}
