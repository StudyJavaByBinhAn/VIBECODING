package com.vibecode.antijob.service;

import com.vibecode.antijob.dto.AppointmentRequest;
import com.vibecode.antijob.dto.AppointmentResponse;
import com.vibecode.antijob.entity.*;
import com.vibecode.antijob.enums.AppointmentStatus;
import com.vibecode.antijob.enums.Gender;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class AppointmentService {

    private final AtomicLong idSeq = new AtomicLong(1);
    private final List<Appointment> store = new ArrayList<>();

    // --- seed data ---
    private final Dentist DENTIST_1 = Dentist.builder()
            .id(1L).fullName("BS. Nguyễn Văn A").phone("0901000001")
            .specialization("Chỉnh nha").licenseNumber("DN-001").active(true).build();

    private final Patient PATIENT_1 = Patient.builder()
            .id(1L).fullName("Trần Thị B").phone("0911000001")
            .dateOfBirth(LocalDate.of(1995, 3, 20))
            .gender(Gender.FEMALE).address("123 Lê Lợi, Q1, TP.HCM").build();

    private final DentalService SERVICE_1 = DentalService.builder()
            .id(1L).name("Làm sạch răng").description("Cạo vôi, đánh bóng").
            durationMinutes(30).price(new BigDecimal("200000")).active(true).build();

    public List<AppointmentResponse> findAll() {
        return store.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public AppointmentResponse findById(Long id) {
        return store.stream()
                .filter(a -> a.getId().equals(id))
                .findFirst()
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch hẹn id=" + id));
    }

    public AppointmentResponse book(AppointmentRequest req) {
        LocalTime endTime = req.getStartTime().plusMinutes(SERVICE_1.getDurationMinutes());
        Appointment appt = Appointment.builder()
                .id(idSeq.getAndIncrement())
                .patient(PATIENT_1)
                .dentist(DENTIST_1)
                .service(SERVICE_1)
                .appointmentDate(req.getAppointmentDate())
                .startTime(req.getStartTime())
                .endTime(endTime)
                .status(AppointmentStatus.PENDING)
                .notes(req.getNotes())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        store.add(appt);
        return toResponse(appt);
    }

    public AppointmentResponse cancel(Long id) {
        Appointment appt = store.stream()
                .filter(a -> a.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch hẹn id=" + id));
        if (appt.getStatus() == AppointmentStatus.CANCELLED) {
            throw new IllegalArgumentException("Lịch hẹn đã bị huỷ trước đó");
        }
        appt.setStatus(AppointmentStatus.CANCELLED);
        appt.setUpdatedAt(LocalDateTime.now());
        return toResponse(appt);
    }

    private AppointmentResponse toResponse(Appointment a) {
        return AppointmentResponse.builder()
                .id(a.getId())
                .patientName(a.getPatient().getFullName())
                .dentistName(a.getDentist().getFullName())
                .serviceName(a.getService().getName())
                .appointmentDate(a.getAppointmentDate())
                .startTime(a.getStartTime())
                .endTime(a.getEndTime())
                .status(a.getStatus())
                .notes(a.getNotes())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
