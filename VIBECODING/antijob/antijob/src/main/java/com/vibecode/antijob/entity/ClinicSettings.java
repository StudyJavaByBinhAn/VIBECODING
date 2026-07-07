package com.vibecode.antijob.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Entity
@Table(name = "clinic_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "clinic_name")
    private String clinicName;

    @Column(name = "open_time")
    private LocalTime openTime;

    @Column(name = "close_time")
    private LocalTime closeTime;

    @Column(name = "slot_duration_minutes")
    private int slotDurationMinutes;

    @Column(name = "max_advance_booking_days")
    private int maxAdvanceBookingDays;

    @Column(name = "buffer_minutes")
    private int bufferMinutes;

    @Column(name = "break_start")
    private LocalTime breakStart;

    @Column(name = "break_end")
    private LocalTime breakEnd;

    @Column(name = "cancel_before_hours")
    private int cancelBeforeHours;

    @Column(name = "max_pending_appointments")
    private int maxPendingAppointments;
}
