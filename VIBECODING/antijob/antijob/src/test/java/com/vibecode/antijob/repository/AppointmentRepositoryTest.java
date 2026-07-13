package com.vibecode.antijob.repository;

import com.vibecode.antijob.entity.Appointment;
import com.vibecode.antijob.entity.DentalService;
import com.vibecode.antijob.entity.Dentist;
import com.vibecode.antijob.entity.Patient;
import com.vibecode.antijob.entity.User;
import com.vibecode.antijob.enums.AppointmentStatus;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AppointmentRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private AppointmentRepository appointmentRepository;

    private Dentist dentist;
    private Patient patient;
    private DentalService service;

    private void seedBaseData() {
        User dentistUser = entityManager.persistFlushFind(
                User.builder().email("bs.a@dental.vn").password("x").role(Role.DENTIST).build());
        dentist = entityManager.persistFlushFind(
                Dentist.builder().user(dentistUser).fullName("BS A").phone("0900000001")
                        .licenseNumber("LIC-TEST-001").active(true).build());

        User patientUser = entityManager.persistFlushFind(
                User.builder().email("patient@dental.vn").password("x").role(Role.PATIENT).build());
        patient = entityManager.persistFlushFind(
                Patient.builder().user(patientUser).fullName("Patient A").phone("0900000002").build());

        service = entityManager.persistFlushFind(
                DentalService.builder().name("Khám tổng quát").durationMinutes(30)
                        .price(java.math.BigDecimal.valueOf(200_000)).active(true).build());
    }

    private Appointment appointmentAt(LocalDate date, LocalTime start, LocalTime end, AppointmentStatus status) {
        return entityManager.persistFlushFind(Appointment.builder()
                .patient(patient)
                .dentist(dentist)
                .service(service)
                .appointmentDate(date)
                .startTime(start)
                .endTime(end)
                .status(status)
                .build());
    }

    @Test
    void findConflictingForUpdate_overlappingActiveAppointment_isReturned() {
        seedBaseData();
        LocalDate date = LocalDate.of(2026, 7, 20);
        appointmentAt(date, LocalTime.of(9, 0), LocalTime.of(9, 30), AppointmentStatus.PENDING);

        List<Appointment> conflicts = appointmentRepository.findConflictingForUpdate(
                dentist.getId(), date, LocalTime.of(9, 15), LocalTime.of(9, 45));

        assertThat(conflicts).hasSize(1);
    }

    @Test
    void findConflictingForUpdate_nonOverlappingAppointment_isNotReturned() {
        seedBaseData();
        LocalDate date = LocalDate.of(2026, 7, 20);
        appointmentAt(date, LocalTime.of(9, 0), LocalTime.of(9, 30), AppointmentStatus.PENDING);

        List<Appointment> conflicts = appointmentRepository.findConflictingForUpdate(
                dentist.getId(), date, LocalTime.of(9, 30), LocalTime.of(10, 0));

        assertThat(conflicts).isEmpty();
    }

    @Test
    void findConflictingForUpdate_cancelledAppointment_isExcluded() {
        seedBaseData();
        LocalDate date = LocalDate.of(2026, 7, 20);
        appointmentAt(date, LocalTime.of(9, 0), LocalTime.of(9, 30), AppointmentStatus.CANCELLED);

        List<Appointment> conflicts = appointmentRepository.findConflictingForUpdate(
                dentist.getId(), date, LocalTime.of(9, 0), LocalTime.of(9, 30));

        assertThat(conflicts).isEmpty();
    }

    @Test
    void countByPatientIdAndStatus_countsOnlyMatchingStatus() {
        seedBaseData();
        LocalDate date = LocalDate.of(2026, 7, 20);
        appointmentAt(date, LocalTime.of(9, 0), LocalTime.of(9, 30), AppointmentStatus.PENDING);
        appointmentAt(date, LocalTime.of(10, 0), LocalTime.of(10, 30), AppointmentStatus.PENDING);
        appointmentAt(date, LocalTime.of(11, 0), LocalTime.of(11, 30), AppointmentStatus.CANCELLED);

        long pendingCount = appointmentRepository.countByPatientIdAndStatus(patient.getId(), AppointmentStatus.PENDING);

        assertThat(pendingCount).isEqualTo(2);
    }

    @Test
    void countByDentistIdAndAppointmentDateAndStatusNot_excludesGivenStatus() {
        seedBaseData();
        LocalDate date = LocalDate.of(2026, 7, 20);
        appointmentAt(date, LocalTime.of(9, 0), LocalTime.of(9, 30), AppointmentStatus.PENDING);
        appointmentAt(date, LocalTime.of(10, 0), LocalTime.of(10, 30), AppointmentStatus.CANCELLED);

        long count = appointmentRepository.countByDentistIdAndAppointmentDateAndStatusNot(
                dentist.getId(), date, AppointmentStatus.CANCELLED);

        assertThat(count).isEqualTo(1);
    }
}
