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

    // Đếm lịch PENDING của 1 patient (giới hạn max_pending_appointments)
    long countByPatientIdAndStatus(Long patientId, AppointmentStatus status);

    // Đếm lịch của 1 dentist trong ngày, không tính CANCELLED (tie-break auto-assign)
    long countByDentistIdAndAppointmentDateAndStatusNot(Long dentistId, LocalDate date, AppointmentStatus status);

    // Batch cho autoAssignDentist: check trùng lịch của N dentist ứng viên trong 1 query,
    // không lock (lock thật sự chỉ xảy ra sau khi đã chọn được dentist, qua findConflictingForUpdate).
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.dentist.id IN :dentistIds
              AND a.appointmentDate = :date
              AND a.status <> 'CANCELLED'
              AND a.startTime < :endTime
              AND a.endTime > :startTime
            """)
    List<Appointment> findConflicting(
            @Param("dentistIds") List<Long> dentistIds,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );

    // Batch tie-break cho autoAssignDentist: đếm lịch/ngày của N dentist trong 1 query (GROUP BY)
    @Query("""
            SELECT a.dentist.id, COUNT(a) FROM Appointment a
            WHERE a.dentist.id IN :dentistIds
              AND a.appointmentDate = :date
              AND a.status <> :status
            GROUP BY a.dentist.id
            """)
    List<Object[]> countByDentistIdsAndAppointmentDateAndStatusNot(
            @Param("dentistIds") List<Long> dentistIds,
            @Param("date") LocalDate date,
            @Param("status") AppointmentStatus status
    );
}
