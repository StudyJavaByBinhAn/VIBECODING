package com.vibecode.antijob.service;

import com.vibecode.antijob.dto.AppointmentRequest;
import com.vibecode.antijob.dto.AppointmentResponse;
import com.vibecode.antijob.entity.Appointment;
import com.vibecode.antijob.entity.Dentist;
import com.vibecode.antijob.entity.DentalService;
import com.vibecode.antijob.entity.Patient;
import com.vibecode.antijob.enums.AppointmentStatus;
import com.vibecode.antijob.repository.AppointmentRepository;
import com.vibecode.antijob.repository.DentalServiceRepository;
import com.vibecode.antijob.repository.DentistRepository;
import com.vibecode.antijob.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DentistRepository dentistRepository;
    private final DentalServiceRepository dentalServiceRepository;

    @Transactional(readOnly = true)
    public List<AppointmentResponse> findAll() {
        return appointmentRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AppointmentResponse findById(Long id, Authentication authentication) {
        Appointment appt = appointmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch hẹn id=" + id));
        assertCanAccess(appt, authentication, true);
        return toResponse(appt);
    }

    @Transactional
    @CacheEvict(cacheNames = "slots", allEntries = true)
    public AppointmentResponse book(AppointmentRequest req, String patientEmail) {
        Patient patient = patientRepository.findByUserEmail(patientEmail)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ bệnh nhân cho tài khoản này"));

        Dentist dentist = dentistRepository.findById(req.getDentistId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bác sĩ id=" + req.getDentistId()));

        DentalService service = dentalServiceRepository.findById(req.getServiceId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy dịch vụ id=" + req.getServiceId()));

        LocalTime endTime = req.getStartTime().plusMinutes(service.getDurationMinutes());

        // Pessimistic lock + kiểm tra conflict
        List<Appointment> conflicts = appointmentRepository.findConflictingForUpdate(
                req.getDentistId(), req.getAppointmentDate(), req.getStartTime(), endTime
        );
        if (!conflicts.isEmpty()) {
            throw new IllegalStateException("Slot đã được đặt, vui lòng chọn giờ khác");
        }

        Appointment appt = Appointment.builder()
                .patient(patient)
                .dentist(dentist)
                .service(service)
                .appointmentDate(req.getAppointmentDate())
                .startTime(req.getStartTime())
                .endTime(endTime)
                .status(AppointmentStatus.PENDING)
                .notes(req.getNotes())
                .build();

        return toResponse(appointmentRepository.save(appt));
    }

    @Transactional
    @CacheEvict(cacheNames = "slots", allEntries = true)
    public AppointmentResponse cancel(Long id, Authentication authentication) {
        Appointment appt = appointmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch hẹn id=" + id));
        assertCanAccess(appt, authentication, false);

        if (appt.getStatus() != AppointmentStatus.PENDING && appt.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new IllegalArgumentException("Chỉ có thể huỷ lịch ở trạng thái PENDING hoặc CONFIRMED");
        }

        appt.setStatus(AppointmentStatus.CANCELLED);
        return toResponse(appointmentRepository.save(appt));
    }

    /**
     * ADMIN luôn được phép. PATIENT chỉ được phép trên lịch hẹn của chính mình.
     * DENTIST chỉ được phép xem (allowDentist=true) lịch hẹn được giao cho mình, không được huỷ.
     */
    private void assertCanAccess(Appointment appt, Authentication authentication, boolean allowDentist) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));
        if (isAdmin) {
            return;
        }

        String email = authentication.getName();
        boolean isOwnerPatient = appt.getPatient().getUser().getEmail().equals(email);
        boolean isAssignedDentist = allowDentist && appt.getDentist().getUser().getEmail().equals(email);

        if (!isOwnerPatient && !isAssignedDentist) {
            throw new AccessDeniedException("Không có quyền truy cập lịch hẹn id=" + appt.getId());
        }
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
