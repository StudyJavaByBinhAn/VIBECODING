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
import com.vibecode.antijob.event.AppointmentBookedPayload;
import com.vibecode.antijob.event.AppointmentCancelledPayload;
import com.vibecode.antijob.event.DomainEvent;
import com.vibecode.antijob.event.KafkaTopics;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final KafkaTemplate<String, Object> kafkaTemplate;
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
        Appointment appt = getAppointmentOrThrow(id);
        assertCanAccess(appt, authentication, true, true);
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

        LocalTime paddedStart = req.getStartTime().minusMinutes(settings.getBufferMinutes());
        LocalTime paddedEnd = endTime.plusMinutes(settings.getBufferMinutes());

        Dentist dentist = (req.getDentistId() != null)
                ? dentistRepository.findById(req.getDentistId())
                        .filter(Dentist::isActive)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Không tìm thấy bác sĩ id=" + req.getDentistId() + " hoặc bác sĩ không còn hoạt động"))
                : autoAssignDentist(req, endTime, paddedStart, paddedEnd);

        // Khoá hẳn hàng dentist trước khi check trùng: nếu chỉ khoá hàng appointment trùng lịch
        // (findConflictingForUpdate), 2 request đặt cùng 1 slot còn trống có thể cùng thấy "chưa
        // có gì để khoá" và cùng lọt qua — khoá dentist buộc request thứ 2 đợi request thứ 1
        // commit/rollback trước, nên lúc check trùng chắc chắn thấy đúng dữ liệu mới nhất.
        dentistRepository.findByIdForUpdate(dentist.getId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bác sĩ id=" + dentist.getId()));

        // Kiểm tra conflict — cộng buffer_minutes cả 2 phía để nhất quán với SlotService
        // (danh sách slot hiển thị) và autoAssignDentist, tránh đặt sát nhau không có khoảng đệm.
        List<Appointment> conflicts = appointmentRepository.findConflictingForUpdate(
                dentist.getId(), req.getAppointmentDate(), paddedStart, paddedEnd
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

        Appointment saved = appointmentRepository.save(appt);
        publishEvent(KafkaTopics.APPOINTMENT_BOOKED, saved.getId().toString(),
                AppointmentBookedPayload.builder()
                        .appointmentId(saved.getId())
                        .patientEmail(patient.getUser().getEmail())
                        .patientName(patient.getFullName())
                        .dentistName(dentist.getFullName())
                        .serviceName(service.getName())
                        .appointmentDate(req.getAppointmentDate())
                        .startTime(req.getStartTime())
                        .build());
        return appointmentMapper.toResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = "slots", allEntries = true)
    public AppointmentResponse cancel(Long id, Authentication authentication) {
        Appointment appt = getAppointmentOrThrow(id);
        assertCanAccess(appt, authentication, false, false);

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
        Appointment saved = appointmentRepository.save(appt);
        publishEvent(KafkaTopics.APPOINTMENT_CANCELLED, saved.getId().toString(),
                AppointmentCancelledPayload.builder()
                        .appointmentId(saved.getId())
                        .patientEmail(appt.getPatient().getUser().getEmail())
                        .dentistName(appt.getDentist().getFullName())
                        .appointmentDate(appt.getAppointmentDate())
                        .startTime(appt.getStartTime())
                        .build());
        return appointmentMapper.toResponse(saved);
    }

    @Transactional
    public AppointmentResponse confirm(Long id, Authentication authentication) {
        Appointment appt = getAppointmentOrThrow(id);
        assertCanManage(appt, authentication);

        if (appt.getStatus() != AppointmentStatus.PENDING) {
            throw new IllegalArgumentException("Chỉ có thể xác nhận lịch ở trạng thái PENDING");
        }

        appt.setStatus(AppointmentStatus.CONFIRMED);
        return appointmentMapper.toResponse(appointmentRepository.save(appt));
    }

    @Transactional
    public AppointmentResponse complete(Long id, Authentication authentication) {
        Appointment appt = getAppointmentOrThrow(id);
        assertCanManage(appt, authentication);

        if (appt.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new IllegalArgumentException("Chỉ có thể hoàn thành lịch ở trạng thái CONFIRMED");
        }

        appt.setStatus(AppointmentStatus.COMPLETED);
        return appointmentMapper.toResponse(appointmentRepository.save(appt));
    }

    @Transactional
    public AppointmentResponse markNoShow(Long id, Authentication authentication) {
        Appointment appt = getAppointmentOrThrow(id);
        assertCanManage(appt, authentication);

        if (appt.getStatus() != AppointmentStatus.PENDING && appt.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new IllegalArgumentException("Chỉ có thể đánh dấu no-show cho lịch ở trạng thái PENDING hoặc CONFIRMED");
        }

        appt.setStatus(AppointmentStatus.NO_SHOW);
        return appointmentMapper.toResponse(appointmentRepository.save(appt));
    }

    private void publishEvent(String topic, String key, Object payload) {
        kafkaTemplate.send(topic, key, DomainEvent.<Object>builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(topic)
                .version(1)
                .occurredAt(Instant.now(clock))
                .data(payload)
                .build());
    }

    private Appointment getAppointmentOrThrow(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch hẹn id=" + id));
    }

    private ClinicSettings getSettingsOrThrow() {
        return clinicSettingsRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Chưa cấu hình clinic_settings"));
    }

    private void validateBookingWindow(LocalDate date, LocalTime startTime, LocalTime endTime, ClinicSettings settings) {
        if (date.isBefore(LocalDate.now(clock))) {
            throw new IllegalArgumentException("Không thể đặt lịch cho ngày trong quá khứ");
        }
        if (date.isAfter(LocalDate.now(clock).plusDays(settings.getMaxAdvanceBookingDays()))) {
            throw new IllegalArgumentException(
                    "Chỉ có thể đặt lịch trước tối đa " + settings.getMaxAdvanceBookingDays() + " ngày");
        }
        if (startTime.isBefore(settings.getOpenTime())) {
            throw new IllegalArgumentException("Không nhận lịch trước giờ mở cửa " + settings.getOpenTime());
        }
        if (settings.getBreakStart() != null && settings.getBreakEnd() != null
                && startTime.isBefore(settings.getBreakEnd()) && endTime.isAfter(settings.getBreakStart())) {
            throw new IllegalArgumentException("Không nhận lịch trong giờ nghỉ trưa");
        }
        if (endTime.isAfter(settings.getCloseTime())) {
            throw new IllegalArgumentException("Không nhận lịch sau giờ đóng cửa " + settings.getCloseTime());
        }
    }

    private Dentist autoAssignDentist(AppointmentRequest req, LocalTime endTime,
                                       LocalTime paddedStart, LocalTime paddedEnd) {
        DayOfWeek dayOfWeek = req.getAppointmentDate().getDayOfWeek();

        List<Dentist> activeDentists = dentistRepository.findByActiveTrue();
        if (activeDentists.isEmpty()) {
            throw new IllegalArgumentException("Không có bác sĩ nào rảnh vào thời gian yêu cầu");
        }
        List<Long> dentistIds = activeDentists.stream().map(Dentist::getId).toList();

        // 3 query gộp thay vì lặp N lần theo từng dentist (trước đây 3N+1 query)
        Map<Long, WorkSchedule> scheduleByDentistId = workScheduleRepository
                .findByDentistIdInAndDayOfWeek(dentistIds, dayOfWeek).stream()
                .collect(Collectors.toMap(ws -> ws.getDentist().getId(), ws -> ws));

        Set<Long> conflictedDentistIds = appointmentRepository
                .findConflicting(dentistIds, req.getAppointmentDate(), paddedStart, paddedEnd).stream()
                .map(a -> a.getDentist().getId())
                .collect(Collectors.toSet());

        List<Dentist> candidates = activeDentists.stream()
                .filter(d -> {
                    WorkSchedule ws = scheduleByDentistId.get(d.getId());
                    return ws != null && ws.isActive()
                            && !req.getStartTime().isBefore(ws.getStartTime())
                            && !endTime.isAfter(ws.getEndTime());
                })
                .filter(d -> !conflictedDentistIds.contains(d.getId()))
                .toList();

        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Không có bác sĩ nào rảnh vào thời gian yêu cầu");
        }

        Map<Long, Long> pendingCountByDentistId = appointmentRepository
                .countByDentistIdsAndAppointmentDateAndStatusNot(
                        candidates.stream().map(Dentist::getId).toList(),
                        req.getAppointmentDate(), AppointmentStatus.CANCELLED)
                .stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        return candidates.stream()
                .min(Comparator.comparingLong(d -> pendingCountByDentistId.getOrDefault(d.getId(), 0L)))
                .orElseThrow();
    }

    /**
     * ADMIN luôn được phép. RECEPTIONIST được phép nếu allowReceptionist=true (xem, không huỷ).
     * PATIENT chỉ được phép trên lịch hẹn của chính mình.
     * DENTIST chỉ được phép xem (allowDentist=true) lịch hẹn được giao cho mình, không được huỷ.
     */
    private void assertCanAccess(Appointment appt, Authentication authentication, boolean allowDentist, boolean allowReceptionist) {
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        if (authorities.contains("ROLE_ADMIN")) {
            return;
        }
        if (allowReceptionist && authorities.contains("ROLE_RECEPTIONIST")) {
            return;
        }

        String email = authentication.getName();
        boolean isOwnerPatient = appt.getPatient().getUser().getEmail().equals(email);
        boolean isAssignedDentist = allowDentist && appt.getDentist().getUser().getEmail().equals(email);

        if (!isOwnerPatient && !isAssignedDentist) {
            throw new AccessDeniedException("Không có quyền truy cập lịch hẹn id=" + appt.getId());
        }
    }

    /**
     * ADMIN và RECEPTIONIST quản lý được mọi lịch hẹn (confirm/complete/no-show).
     * DENTIST chỉ quản lý được lịch hẹn được giao cho mình. PATIENT không có quyền.
     */
    private void assertCanManage(Appointment appt, Authentication authentication) {
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        if (authorities.contains("ROLE_ADMIN") || authorities.contains("ROLE_RECEPTIONIST")) {
            return;
        }

        String email = authentication.getName();
        boolean isAssignedDentist = appt.getDentist().getUser().getEmail().equals(email);
        if (!isAssignedDentist) {
            throw new AccessDeniedException("Không có quyền cập nhật lịch hẹn id=" + appt.getId());
        }
    }
}
