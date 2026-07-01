package com.vibecode.antijob.repository;

import com.vibecode.antijob.entity.Appointment;
import com.vibecode.antijob.enums.AppointmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    // Pessimistic lock: ngăn double-booking cùng slot
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.dentist.id = :dentistId
              AND a.appointmentDate = :date
              AND a.status <> 'CANCELLED'
              AND a.startTime < :endTime
              AND a.endTime > :startTime
            """)
    List<Appointment> findConflictingForUpdate(
            @Param("dentistId") Long dentistId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );

    // Lấy lịch hẹn đã đặt của bác sĩ trong ngày (dùng cho SlotService)
    List<Appointment> findByDentistIdAndAppointmentDateAndStatusNot(
            Long dentistId, LocalDate date, AppointmentStatus status
    );
}
