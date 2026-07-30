package com.vibecode.antijob.repository;

import com.vibecode.antijob.entity.WorkSchedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, Long> {

    // Lock row này trước khi check conflict → tránh race condition
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ws FROM WorkSchedule ws WHERE ws.dentist.id = :dentistId AND ws.dayOfWeek = :dayOfWeek")
    Optional<WorkSchedule> findByDentistIdAndDayOfWeekForUpdate(
            @Param("dentistId") Long dentistId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek
    );

    Optional<WorkSchedule> findByDentistIdAndDayOfWeek(Long dentistId, DayOfWeek dayOfWeek);

    List<WorkSchedule> findByDentistId(Long dentistId);

    // Batch cho autoAssignDentist: 1 query thay vì N query lặp theo từng dentist
    List<WorkSchedule> findByDentistIdInAndDayOfWeek(List<Long> dentistIds, DayOfWeek dayOfWeek);
}
