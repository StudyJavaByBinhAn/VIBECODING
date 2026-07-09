package com.vibecode.antijob.service;

import com.vibecode.antijob.dto.AppointmentRequest;
import com.vibecode.antijob.dto.AppointmentResponse;
import com.vibecode.antijob.dto.PageResponse;
import com.vibecode.antijob.entity.Appointment;
import com.vibecode.antijob.entity.ClinicSettings;
import com.vibecode.antijob.entity.Dentist;
import com.vibecode.antijob.entity.DentalService;
import com.vibecode.antijob.entity.Patient;
import com.vibecode.antijob.entity.WorkSchedule;
import com.vibecode.antijob.enums.AppointmentStatus;
import com.vibecode.antijob.mapper.AppointmentMapper;
import com.vibecode.antijob.repository.AppointmentRepository;
import com.vibecode.antijob.repository.ClinicSettingsRepository;
import com.vibecode.antijob.repository.DentalServiceRepository;
import com.vibecode.antijob.repository.DentistRepository;
import com.vibecode.antijob.repository.PatientRepository;
import com.vibecode.antijob.repository.WorkScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DentistRepository dentistRepository;
    private final DentalServiceRepository dentalServiceRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final ClinicSettingsRepository clinicSettingsRepository;
    private final AppointmentMapper appointmentMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> findAll(Pageable pageable) {
        Page<AppointmentResponse> page = appointmentRepository.findAll(pageable).map(appointmentMapper::toResponse);
        return PageResponse.<AppointmentResponse>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public AppointmentResponse findById(Long id, Authentication authentication) {
        Appointment appt = appointmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch hẹn id=" + id));
        assertCanAccess(appt, authentication, true);
        return appointmentMapper.toResponse(appt);
    }

    @Transactional
    @CacheEvict(cacheNames = "slots", allEntries = true)
    public AppointmentResponse book(AppointmentRequest req, String patientEmail) {
        ClinicSettings settings = getSettingsOrThrow();

        Patient patient = patientRepository.findByUserEmail(patientEmail)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ bệnh nhân cho tài khoản này"));

        if (appointmentRepository.countByPatientIdAndStatus(patient.getId(), AppointmentStatus.PENDING)
                >= settings.getMaxPendingAppointments()) {
            throw new IllegalArgumentException(
                    "Đã đạt giới hạn " + settings.getMaxPendingAppointments() + " lịch hẹn đang chờ");
        }

        DentalService service = dentalServiceRepository.findById(req.getServiceId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy dịch vụ id=" + req.getServiceId()));

        LocalTime endTime = req.getStartTime().plusMinutes(service.getDurationMinutes());
        validateBookingWindow(req.getAppointmentDate(), req.getStartTime(), endTime, settings);

        Dentist dentist = (req.getDentistId() != null)
                ? dentistRepository.findById(req.getDentistId())
                        .filter(Dentist::isActive)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Không tìm thấy bác sĩ id=" + req.getDentistId() + " hoặc bác sĩ không còn hoạt động"))
                : autoAssignDentist(req, endTime, settings);

        // Pessimistic lock + kiểm tra conflict
        List<Appointment> conflicts = appointmentRepository.findConflictingForUpdate(
                dentist.getId(), req.getAppointmentDate(), req.getStartTime(), endTime
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

        return appointmentMapper.toResponse(appointmentRepository.save(appt));
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

        ClinicSettings settings = getSettingsOrThrow();
        LocalDateTime appointmentDateTime = LocalDateTime.of(appt.getAppointmentDate(), appt.getStartTime());
        if (LocalDateTime.now(clock).plusHours(settings.getCancelBeforeHours()).isAfter(appointmentDateTime)) {
            throw new IllegalArgumentException(
                    "Không thể huỷ lịch trong vòng " + settings.getCancelBeforeHours() + " giờ trước giờ hẹn");
        }

        appt.setStatus(AppointmentStatus.CANCELLED);
        return appointmentMapper.toResponse(appointmentRepository.save(appt));
    }

    private ClinicSettings getSettingsOrThrow() {
        return clinicSettingsRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Chưa cấu hình clinic_settings"));
    }

    private void validateBookingWindow(LocalDate date, LocalTime startTime, LocalTime endTime, ClinicSettings settings) {
        if (date.isBefore(LocalDate.now(clock))) {
            throw new IllegalArgumentException("Không thể đặt lịch cho ngày trong quá khứ");
        }
        if (settings.getBreakStart() != null && settings.getBreakEnd() != null
                && startTime.isBefore(settings.getBreakEnd()) && endTime.isAfter(settings.getBreakStart())) {
            throw new IllegalArgumentException("Không nhận lịch trong giờ nghỉ trưa");
        }
        if (endTime.isAfter(settings.getCloseTime())) {
            throw new IllegalArgumentException("Không nhận lịch sau giờ đóng cửa " + settings.getCloseTime());
        }
    }

    private Dentist autoAssignDentist(AppointmentRequest req, LocalTime endTime, ClinicSettings settings) {
        DayOfWeek dayOfWeek = req.getAppointmentDate().getDayOfWeek();
        LocalTime paddedStart = req.getStartTime().minusMinutes(settings.getBufferMinutes());
        LocalTime paddedEnd = endTime.plusMinutes(settings.getBufferMinutes());

        List<Dentist> candidates = dentistRepository.findByActiveTrue().stream()
                .filter(d -> workScheduleRepository.findByDentistIdAndDayOfWeek(d.getId(), dayOfWeek)
                        .filter(WorkSchedule::isActive)
                        .filter(ws -> !req.getStartTime().isBefore(ws.getStartTime()) && !endTime.isAfter(ws.getEndTime()))
                        .isPresent())
                .filter(d -> appointmentRepository.findConflictingForUpdate(
                        d.getId(), req.getAppointmentDate(), paddedStart, paddedEnd).isEmpty())
                .toList();

        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Không có bác sĩ nào rảnh vào thời gian yêu cầu");
        }

        return candidates.stream()
                .min(Comparator.comparingLong(d -> appointmentRepository.countByDentistIdAndAppointmentDateAndStatusNot(
                        d.getId(), req.getAppointmentDate(), AppointmentStatus.CANCELLED)))
                .orElseThrow();
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
}
