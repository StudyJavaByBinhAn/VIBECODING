package com.vibecode.antijob.repository;

import com.vibecode.antijob.entity.Dentist;
import com.vibecode.antijob.entity.User;
import com.vibecode.antijob.entity.WorkSchedule;
import com.vibecode.antijob.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class WorkScheduleRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private WorkScheduleRepository workScheduleRepository;

    private Dentist seedDentist() {
        User dentistUser = entityManager.persistFlushFind(
                User.builder().email("bs.b@dental.vn").password("x").role(Role.DENTIST).build());
        return entityManager.persistFlushFind(
                Dentist.builder().user(dentistUser).fullName("BS B").phone("0900000003")
                        .licenseNumber("LIC-TEST-002").active(true).build());
    }

    @Test
    void findByDentistIdAndDayOfWeek_returnsMatchingSchedule() {
        Dentist dentist = seedDentist();
        entityManager.persistFlushFind(WorkSchedule.builder()
                .dentist(dentist).dayOfWeek(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0)).active(true).build());

        Optional<WorkSchedule> found = workScheduleRepository.findByDentistIdAndDayOfWeek(dentist.getId(), DayOfWeek.MONDAY);

        assertThat(found).isPresent();
        assertThat(found.get().getStartTime()).isEqualTo(LocalTime.of(8, 0));
    }

    @Test
    void findByDentistIdAndDayOfWeek_noSchedule_returnsEmpty() {
        Dentist dentist = seedDentist();

        Optional<WorkSchedule> found = workScheduleRepository.findByDentistIdAndDayOfWeek(dentist.getId(), DayOfWeek.SUNDAY);

        assertThat(found).isEmpty();
    }

    @Test
    void duplicateDayOfWeekForSameDentist_violatesUniqueConstraint() {
        Dentist dentist = seedDentist();
        entityManager.persistFlushFind(WorkSchedule.builder()
                .dentist(dentist).dayOfWeek(DayOfWeek.TUESDAY)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0)).active(true).build());

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                entityManager.persistFlushFind(WorkSchedule.builder()
                        .dentist(dentist).dayOfWeek(DayOfWeek.TUESDAY)
                        .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(18, 0)).active(true).build())
        ).isInstanceOf(RuntimeException.class);
    }

    @Test
    void findByDentistId_returnsAllSchedulesForDentist() {
        Dentist dentist = seedDentist();
        entityManager.persistFlushFind(WorkSchedule.builder()
                .dentist(dentist).dayOfWeek(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0)).active(true).build());
        entityManager.persistFlushFind(WorkSchedule.builder()
                .dentist(dentist).dayOfWeek(DayOfWeek.WEDNESDAY)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(12, 0)).active(true).build());

        assertThat(workScheduleRepository.findByDentistId(dentist.getId())).hasSize(2);
    }

    @Test
    void findByDentistIdAndDayOfWeekForUpdate_locksAndReturnsRow() {
        Dentist dentist = seedDentist();
        entityManager.persistFlushFind(WorkSchedule.builder()
                .dentist(dentist).dayOfWeek(DayOfWeek.FRIDAY)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0)).active(true).build());

        Optional<WorkSchedule> found = workScheduleRepository.findByDentistIdAndDayOfWeekForUpdate(dentist.getId(), DayOfWeek.FRIDAY);

        assertThat(found).isPresent();
    }
}
