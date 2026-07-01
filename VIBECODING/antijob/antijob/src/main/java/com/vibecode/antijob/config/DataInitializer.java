package com.vibecode.antijob.config;

import com.vibecode.antijob.entity.*;
import com.vibecode.antijob.enums.AppointmentStatus;
import com.vibecode.antijob.enums.Gender;
import com.vibecode.antijob.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer {

    private final PatientRepository patientRepository;
    private final DentistRepository dentistRepository;
    private final DentalServiceRepository dentalServiceRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final ClinicSettingsRepository clinicSettingsRepository;
    private final AppointmentRepository appointmentRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (dentistRepository.count() > 0) {
            log.info("Data đã tồn tại — bỏ qua seed");
            return;
        }

        log.info("Bắt đầu seed data...");

        // --- Dentists ---
        Dentist dentistA = dentistRepository.save(Dentist.builder()
                .fullName("BS. Nguyễn Văn A").phone("0901000001")
                .specialization("Chỉnh nha").licenseNumber("DN-001")
                .bio("Chuyên gia niềng răng 10 năm kinh nghiệm.").active(true).build());

        Dentist dentistB = dentistRepository.save(Dentist.builder()
                .fullName("BS. Trần Thị B").phone("0901000002")
                .specialization("Nha khoa tổng quát").licenseNumber("DN-002")
                .bio("Điều trị tổng quát và thẩm mỹ răng.").active(true).build());

        // --- Patients ---
        Patient patientC = patientRepository.save(Patient.builder()
                .fullName("Lê Văn C").phone("0911000001")
                .dateOfBirth(LocalDate.of(1995, 3, 20))
                .gender(Gender.MALE).address("123 Lê Lợi, Q1, TP.HCM").build());

        Patient patientD = patientRepository.save(Patient.builder()
                .fullName("Phạm Thị D").phone("0911000002")
                .dateOfBirth(LocalDate.of(1990, 7, 15))
                .gender(Gender.FEMALE).address("456 Nguyễn Huệ, Q1, TP.HCM").build());

        // --- Dental Services ---
        DentalService svcClean = dentalServiceRepository.save(DentalService.builder()
                .name("Làm sạch răng").description("Cạo vôi răng và đánh bóng")
                .durationMinutes(30).price(new BigDecimal("200000")).active(true).build());

        DentalService svcFill = dentalServiceRepository.save(DentalService.builder()
                .name("Trám răng").description("Trám composite thẩm mỹ")
                .durationMinutes(45).price(new BigDecimal("350000")).active(true).build());

        dentalServiceRepository.save(DentalService.builder()
                .name("Nhổ răng").description("Nhổ răng thường (không phẫu thuật)")
                .durationMinutes(30).price(new BigDecimal("300000")).active(true).build());

        dentalServiceRepository.save(DentalService.builder()
                .name("Niềng răng mắc cài").description("Tư vấn và lắp mắc cài kim loại")
                .durationMinutes(60).price(new BigDecimal("1500000")).active(true).build());

        dentalServiceRepository.save(DentalService.builder()
                .name("Tẩy trắng răng").description("Tẩy trắng bằng laser")
                .durationMinutes(60).price(new BigDecimal("800000")).active(true).build());

        // --- Work Schedules — BS. Nguyễn Văn A: Thứ 2–6, 8h–17h ---
        for (DayOfWeek day : new DayOfWeek[]{
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY}) {
            workScheduleRepository.save(WorkSchedule.builder()
                    .dentist(dentistA).dayOfWeek(day)
                    .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0))
                    .active(true).build());
        }

        // --- Work Schedules — BS. Trần Thị B: Thứ 2, 4, 6 + Thứ 7 sáng ---
        for (DayOfWeek day : new DayOfWeek[]{
                DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY}) {
            workScheduleRepository.save(WorkSchedule.builder()
                    .dentist(dentistB).dayOfWeek(day)
                    .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0))
                    .active(true).build());
        }
        workScheduleRepository.save(WorkSchedule.builder()
                .dentist(dentistB).dayOfWeek(DayOfWeek.SATURDAY)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(12, 0))
                .active(true).build());

        // --- Clinic Settings ---
        clinicSettingsRepository.save(ClinicSettings.builder()
                .clinicName("Nha Khoa VibeCode")
                .openTime(LocalTime.of(8, 0)).closeTime(LocalTime.of(17, 0))
                .slotDurationMinutes(30).maxAdvanceBookingDays(30).build());

        // --- Sample Appointments ---
        appointmentRepository.save(Appointment.builder()
                .patient(patientC).dentist(dentistA).service(svcClean)
                .appointmentDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(9, 30))
                .status(AppointmentStatus.CONFIRMED).notes("Lần đầu khám").build());

        appointmentRepository.save(Appointment.builder()
                .patient(patientD).dentist(dentistB).service(svcFill)
                .appointmentDate(LocalDate.now().plusDays(2))
                .startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(10, 45))
                .status(AppointmentStatus.PENDING).build());

        log.info("Seed data hoàn thành.");
    }
}
