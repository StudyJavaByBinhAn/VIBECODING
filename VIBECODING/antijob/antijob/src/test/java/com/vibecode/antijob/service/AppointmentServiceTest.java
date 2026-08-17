package com.vibecode.antijob.service;

import com.vibecode.antijob.dto.AppointmentRequest;
import com.vibecode.antijob.dto.AppointmentResponse;
import com.vibecode.antijob.entity.Appointment;
import com.vibecode.antijob.entity.ClinicSettings;
import com.vibecode.antijob.entity.DentalService;
import com.vibecode.antijob.entity.Dentist;
import com.vibecode.antijob.entity.Patient;
import com.vibecode.antijob.entity.User;
import com.vibecode.antijob.entity.WorkSchedule;
import com.vibecode.antijob.enums.AppointmentStatus;
import com.vibecode.antijob.event.DomainEvent;
import com.vibecode.antijob.event.KafkaTopics;
import com.vibecode.antijob.mapper.AppointmentMapper;
import com.vibecode.antijob.repository.AppointmentRepository;
import com.vibecode.antijob.repository.ClinicSettingsRepository;
import com.vibecode.antijob.repository.DentalServiceRepository;
import com.vibecode.antijob.repository.DentistRepository;
import com.vibecode.antijob.repository.PatientRepository;
import com.vibecode.antijob.repository.WorkScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 15);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            TODAY.atTime(10, 0).atZone(ZONE).toInstant(), ZONE);
    private static final String PATIENT_EMAIL = "patient@example.com";

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private DentistRepository dentistRepository;
    @Mock
    private DentalServiceRepository dentalServiceRepository;
    @Mock
    private WorkScheduleRepository workScheduleRepository;
    @Mock
    private ClinicSettingsRepository clinicSettingsRepository;
    @Mock
    private AppointmentMapper appointmentMapper;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private AppointmentService appointmentService;

    private Patient patient;
    private Dentist activeDentist;
    private DentalService service;
    private ClinicSettings settings;

    @BeforeEach
    void setUp() {
        appointmentService = new AppointmentService(appointmentRepository, patientRepository, dentistRepository,
                dentalServiceRepository, workScheduleRepository, clinicSettingsRepository, appointmentMapper,
                kafkaTemplate, FIXED_CLOCK);

        patient = Patient.builder().id(1L).user(User.builder().email(PATIENT_EMAIL).build()).fullName("Nguyen Van A").build();
        activeDentist = Dentist.builder().id(10L).active(true)
                .user(User.builder().email("dentist@example.com").build()).fullName("BS A").build();
        service = DentalService.builder().id(100L).name("Kham tong quat").durationMinutes(30).build();
        settings = ClinicSettings.builder()
                .bufferMinutes(10)
                .openTime(LocalTime.of(8, 0))
                .breakStart(LocalTime.of(12, 0))
                .breakEnd(LocalTime.of(13, 0))
                .closeTime(LocalTime.of(17, 0))
                .cancelBeforeHours(12)
                .maxPendingAppointments(3)
                .maxAdvanceBookingDays(30)
                .build();
    }

    private AppointmentRequest request(Long dentistId, LocalDate date, LocalTime startTime) {
        AppointmentRequest req = new AppointmentRequest();
        req.setDentistId(dentistId);
        req.setServiceId(100L);
        req.setAppointmentDate(date);
        req.setStartTime(startTime);
        req.setNotes("test");
        return req;
    }

    private Authentication adminAuth() {
        Authentication auth = mock(Authentication.class);
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        doReturn(authorities).when(auth).getAuthorities();
        return auth;
    }

    private Authentication patientAuth(String email) {
        Authentication auth = mock(Authentication.class);
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_PATIENT"));
        doReturn(authorities).when(auth).getAuthorities();
        when(auth.getName()).thenReturn(email);
        return auth;
    }

    private Authentication receptionistAuth() {
        Authentication auth = mock(Authentication.class);
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_RECEPTIONIST"));
        doReturn(authorities).when(auth).getAuthorities();
        return auth;
    }

    private Authentication dentistAuth(String email) {
        Authentication auth = mock(Authentication.class);
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_DENTIST"));
        doReturn(authorities).when(auth).getAuthorities();
        when(auth.getName()).thenReturn(email);
        return auth;
    }

    // ---------- book() ----------

    @Test
    void book_happyPath_explicitActiveDentist_savesPendingAppointment() {
        LocalDate future = TODAY.plusDays(1);
        AppointmentRequest req = request(10L, future, LocalTime.of(9, 0));

        when(clinicSettingsRepository.findAll()).thenReturn(List.of(settings));
        when(patientRepository.findByUserEmail(PATIENT_EMAIL)).thenReturn(Optional.of(patient));
        when(appointmentRepository.countByPatientIdAndStatus(1L, AppointmentStatus.PENDING)).thenReturn(0L);
        when(dentalServiceRepository.findById(100L)).thenReturn(Optional.of(service));
        when(dentistRepository.findById(10L)).thenReturn(Optional.of(activeDentist));
        when(dentistRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activeDentist));
        when(appointmentRepository.findConflictingForUpdate(eq(10L), eq(future), any(), any())).thenReturn(List.of());
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(999L);
            }
            return saved;
        });
        when(appointmentMapper.toResponse(any(Appointment.class))).thenReturn(AppointmentResponse.builder().build());

        appointmentService.book(req, PATIENT_EMAIL);

        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(captor.getValue().getDentist()).isEqualTo(activeDentist);
        verify(dentistRepository).findByIdForUpdate(10L);
        verify(kafkaTemplate).send(eq(KafkaTopics.APPOINTMENT_BOOKED), eq("999"), any());
    }

    @Test
    void book_conflict_throwsIllegalState_doesNotSave() {
        LocalDate future = TODAY.plusDays(1);
        AppointmentRequest req = request(10L, future, LocalTime.of(9, 0));

        when(clinicSettingsRepository.findAll()).thenReturn(List.of(settings));
        when(patientRepository.findByUserEmail(PATIENT_EMAIL)).thenReturn(Optional.of(patient));
        when(appointmentRepository.countByPatientIdAndStatus(1L, AppointmentStatus.PENDING)).thenReturn(0L);
        when(dentalServiceRepository.findById(100L)).thenReturn(Optional.of(service));
        when(dentistRepository.findById(10L)).thenReturn(Optional.of(activeDentist));
        when(dentistRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activeDentist));
        when(appointmentRepository.findConflictingForUpdate(eq(10L), eq(future), any(), any()))
                .thenReturn(List.of(Appointment.builder().build()));

        assertThatThrownBy(() -> appointmentService.book(req, PATIENT_EMAIL))
                .isInstanceOf(IllegalStateException.class);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void book_pastDate_throwsIllegalArgument() {
        AppointmentRequest req = request(10L, TODAY.minusDays(1), LocalTime.of(9, 0));

        when(clinicSettingsRepository.findAll()).thenReturn(List.of(settings));
        when(patientRepository.findByUserEmail(PATIENT_EMAIL)).thenReturn(Optional.of(patient));
        when(appointmentRepository.countByPatientIdAndStatus(1L, AppointmentStatus.PENDING)).thenReturn(0L);
        when(dentalServiceRepository.findById(100L)).thenReturn(Optional.of(service));

        assertThatThrownBy(() -> appointmentService.book(req, PATIENT_EMAIL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void book_withinBreakWindow_throwsIllegalArgument() {
        LocalDate future = TODAY.plusDays(1);
        AppointmentRequest req = request(10L, future, LocalTime.of(12, 15));

        when(clinicSettingsRepository.findAll()).thenReturn(List.of(settings));
        when(patientRepository.findByUserEmail(PATIENT_EMAIL)).thenReturn(Optional.of(patient));
        when(appointmentRepository.countByPatientIdAndStatus(1L, AppointmentStatus.PENDING)).thenReturn(0L);
        when(dentalServiceRepository.findById(100L)).thenReturn(Optional.of(service));

        assertThatThrownBy(() -> appointmentService.book(req, PATIENT_EMAIL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void book_afterCloseTime_throwsIllegalArgument() {
        LocalDate future = TODAY.plusDays(1);
        AppointmentRequest req = request(10L, future, LocalTime.of(16, 45));

        when(clinicSettingsRepository.findAll()).thenReturn(List.of(settings));
        when(patientRepository.findByUserEmail(PATIENT_EMAIL)).thenReturn(Optional.of(patient));
        when(appointmentRepository.countByPatientIdAndStatus(1L, AppointmentStatus.PENDING)).thenReturn(0L);
        when(dentalServiceRepository.findById(100L)).thenReturn(Optional.of(service));

        assertThatThrownBy(() -> appointmentService.book(req, PATIENT_EMAIL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void book_beforeOpenTime_throwsIllegalArgument() {
        LocalDate future = TODAY.plusDays(1);
        AppointmentRequest req = request(10L, future, LocalTime.of(7, 0));

        when(clinicSettingsRepository.findAll()).thenReturn(List.of(settings));
        when(patientRepository.findByUserEmail(PATIENT_EMAIL)).thenReturn(Optional.of(patient));
        when(appointmentRepository.countByPatientIdAndStatus(1L, AppointmentStatus.PENDING)).thenReturn(0L);
        when(dentalServiceRepository.findById(100L)).thenReturn(Optional.of(service));

        assertThatThrownBy(() -> appointmentService.book(req, PATIENT_EMAIL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void book_beyondMaxAdvanceBookingDays_throwsIllegalArgument() {
        LocalDate tooFar = TODAY.plusDays(settings.getMaxAdvanceBookingDays() + 1);
        AppointmentRequest req = request(10L, tooFar, LocalTime.of(9, 0));

        when(clinicSettingsRepository.findAll()).thenReturn(List.of(settings));
        when(patientRepository.findByUserEmail(PATIENT_EMAIL)).thenReturn(Optional.of(patient));
        when(appointmentRepository.countByPatientIdAndStatus(1L, AppointmentStatus.PENDING)).thenReturn(0L);
        when(dentalServiceRepository.findById(100L)).thenReturn(Optional.of(service));

        assertThatThrownBy(() -> appointmentService.book(req, PATIENT_EMAIL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void book_withinMaxAdvanceBookingDays_succeeds() {
        LocalDate exactlyAtLimit = TODAY.plusDays(settings.getMaxAdvanceBookingDays());
        AppointmentRequest req = request(10L, exactlyAtLimit, LocalTime.of(9, 0));

        when(clinicSettingsRepository.findAll()).thenReturn(List.of(settings));
        when(patientRepository.findByUserEmail(PATIENT_EMAIL)).thenReturn(Optional.of(patient));
        when(appointmentRepository.countByPatientIdAndStatus(1L, AppointmentStatus.PENDING)).thenReturn(0L);
        when(dentalServiceRepository.findById(100L)).thenReturn(Optional.of(service));
        when(dentistRepository.findById(10L)).thenReturn(Optional.of(activeDentist));
        when(dentistRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activeDentist));
        when(appointmentRepository.findConflictingForUpdate(eq(10L), eq(exactlyAtLimit), any(), any())).thenReturn(List.of());
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(999L);
            }
            return saved;
        });
        when(appointmentMapper.toResponse(any(Appointment.class))).thenReturn(AppointmentResponse.builder().build());

        appointmentService.book(req, PATIENT_EMAIL);

        verify(appointmentRepository).save(any());
    }

    @Test
    void book_explicitDentist_conflictCheckAppliesBufferMinutes() {
        // Yêu cầu đặt lịch bắt đầu đúng lúc 1 lịch hẹn khác kết thúc (09:00) — với buffer 10 phút
        // (settings.bufferMinutes=10), cửa sổ check trùng thực tế phải là [08:50, 09:40], không
        // phải [09:00, 09:30] thô — nếu không sẽ cho phép đặt sát nhau không có khoảng đệm.
        LocalDate future = TODAY.plusDays(1);
        AppointmentRequest req = request(10L, future, LocalTime.of(9, 0));

        when(clinicSettingsRepository.findAll()).thenReturn(List.of(settings));
        when(patientRepository.findByUserEmail(PATIENT_EMAIL)).thenReturn(Optional.of(patient));
        when(appointmentRepository.countByPatientIdAndStatus(1L, AppointmentStatus.PENDING)).thenReturn(0L);
        when(dentalServiceRepository.findById(100L)).thenReturn(Optional.of(service));
        when(dentistRepository.findById(10L)).thenReturn(Optional.of(activeDentist));
        when(dentistRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activeDentist));
        when(appointmentRepository.findConflictingForUpdate(eq(10L), eq(future), any(), any())).thenReturn(List.of());
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(999L);
            }
            return saved;
        });
        when(appointmentMapper.toResponse(any(Appointment.class))).thenReturn(AppointmentResponse.builder().build());

        appointmentService.book(req, PATIENT_EMAIL);

        verify(appointmentRepository).findConflictingForUpdate(
                10L, future, LocalTime.of(8, 50), LocalTime.of(9, 40));
    }

    @Test
    void book_maxPendingReached_throwsIllegalArgument_skipsDentistAndServiceLookup() {
        LocalDate future = TODAY.plusDays(1);
        AppointmentRequest req = request(10L, future, LocalTime.of(9, 0));

        when(clinicSettingsRepository.findAll()).thenReturn(List.of(settings));
        when(patientRepository.findByUserEmail(PATIENT_EMAIL)).thenReturn(Optional.of(patient));
        when(appointmentRepository.countByPatientIdAndStatus(1L, AppointmentStatus.PENDING)).thenReturn(3L);

        assertThatThrownBy(() -> appointmentService.book(req, PATIENT_EMAIL))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(dentalServiceRepository, dentistRepository);
    }

    @Test
    void book_explicitDentistInactive_throwsIllegalArgument() {
        LocalDate future = TODAY.plusDays(1);
        AppointmentRequest req = request(10L, future, LocalTime.of(9, 0));
        Dentist inactiveDentist = Dentist.builder().id(10L).active(false).build();

        when(clinicSettingsRepository.findAll()).thenReturn(List.of(settings));
        when(patientRepository.findByUserEmail(PATIENT_EMAIL)).thenReturn(Optional.of(patient));
        when(appointmentRepository.countByPatientIdAndStatus(1L, AppointmentStatus.PENDING)).thenReturn(0L);
        when(dentalServiceRepository.findById(100L)).thenReturn(Optional.of(service));
        when(dentistRepository.findById(10L)).thenReturn(Optional.of(inactiveDentist));

        assertThatThrownBy(() -> appointmentService.book(req, PATIENT_EMAIL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void book_autoAssign_singleCandidate_assignsThatDentist() {
        LocalDate future = TODAY.plusDays(1);
        AppointmentRequest req = request(null, future, LocalTime.of(9, 0));
        WorkSchedule schedule = WorkSchedule.builder().dentist(activeDentist)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0)).active(true).build();

        when(clinicSettingsRepository.findAll()).thenReturn(List.of(settings));
        when(patientRepository.findByUserEmail(PATIENT_EMAIL)).thenReturn(Optional.of(patient));
        when(appointmentRepository.countByPatientIdAndStatus(1L, AppointmentStatus.PENDING)).thenReturn(0L);
        when(dentalServiceRepository.findById(100L)).thenReturn(Optional.of(service));
        when(dentistRepository.findByActiveTrue()).thenReturn(List.of(activeDentist));
        when(dentistRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activeDentist));
        when(workScheduleRepository.findByDentistIdInAndDayOfWeek(anyList(), any())).thenReturn(List.of(schedule));
        when(appointmentRepository.findConflicting(anyList(), any(), any(), any())).thenReturn(List.of());
        when(appointmentRepository.countByDentistIdsAndAppointmentDateAndStatusNot(anyList(), eq(future), eq(AppointmentStatus.CANCELLED)))
                .thenReturn(Collections.singletonList(new Object[]{10L, 0L}));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(999L);
            }
            return saved;
        });
        when(appointmentMapper.toResponse(any(Appointment.class))).thenReturn(AppointmentResponse.builder().build());

        appointmentService.book(req, PATIENT_EMAIL);

        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(captor.capture());
        assertThat(captor.getValue().getDentist().getId()).isEqualTo(10L);
    }

    @Test
    void book_autoAssign_multipleCandidates_picksFewestAppointmentsToday() {
        LocalDate future = TODAY.plusDays(1);
        AppointmentRequest req = request(null, future, LocalTime.of(9, 0));
        Dentist busyDentist = Dentist.builder().id(10L).active(true).build();
        Dentist freeDentist = Dentist.builder().id(20L).active(true).build();
        WorkSchedule busySchedule = WorkSchedule.builder().dentist(busyDentist)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0)).active(true).build();
        WorkSchedule freeSchedule = WorkSchedule.builder().dentist(freeDentist)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0)).active(true).build();

        when(clinicSettingsRepository.findAll()).thenReturn(List.of(settings));
        when(patientRepository.findByUserEmail(PATIENT_EMAIL)).thenReturn(Optional.of(patient));
        when(appointmentRepository.countByPatientIdAndStatus(1L, AppointmentStatus.PENDING)).thenReturn(0L);
        when(dentalServiceRepository.findById(100L)).thenReturn(Optional.of(service));
        when(dentistRepository.findByActiveTrue()).thenReturn(List.of(busyDentist, freeDentist));
        when(dentistRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(freeDentist));
        when(workScheduleRepository.findByDentistIdInAndDayOfWeek(anyList(), any())).thenReturn(List.of(busySchedule, freeSchedule));
        when(appointmentRepository.findConflicting(anyList(), any(), any(), any())).thenReturn(List.of());
        when(appointmentRepository.countByDentistIdsAndAppointmentDateAndStatusNot(anyList(), eq(future), eq(AppointmentStatus.CANCELLED)))
                .thenReturn(List.<Object[]>of(new Object[]{10L, 2L}, new Object[]{20L, 0L}));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(999L);
            }
            return saved;
        });
        when(appointmentMapper.toResponse(any(Appointment.class))).thenReturn(AppointmentResponse.builder().build());

        appointmentService.book(req, PATIENT_EMAIL);

        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(captor.capture());
        assertThat(captor.getValue().getDentist().getId()).isEqualTo(20L);
    }

    @Test
    void book_autoAssign_noCandidateAvailable_throwsIllegalArgument() {
        LocalDate future = TODAY.plusDays(1);
        AppointmentRequest req = request(null, future, LocalTime.of(9, 0));

        when(clinicSettingsRepository.findAll()).thenReturn(List.of(settings));
        when(patientRepository.findByUserEmail(PATIENT_EMAIL)).thenReturn(Optional.of(patient));
        when(appointmentRepository.countByPatientIdAndStatus(1L, AppointmentStatus.PENDING)).thenReturn(0L);
        when(dentalServiceRepository.findById(100L)).thenReturn(Optional.of(service));
        when(dentistRepository.findByActiveTrue()).thenReturn(List.of(activeDentist));
        when(workScheduleRepository.findByDentistIdInAndDayOfWeek(anyList(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> appointmentService.book(req, PATIENT_EMAIL))
                .isInstanceOf(IllegalArgumentException.class);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void book_missingClinicSettings_throwsIllegalState_skipsPatientLookup() {
        AppointmentRequest req = request(10L, TODAY.plusDays(1), LocalTime.of(9, 0));

        when(clinicSettingsRepository.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> appointmentService.book(req, PATIENT_EMAIL))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(patientRepository);
    }

    // ---------- cancel() ----------

    @Test
    void cancel_earlyEnough_succeeds() {
        Appointment appt = Appointment.builder().id(1L).status(AppointmentStatus.PENDING)
                .appointmentDate(TODAY.plusDays(5)).startTime(LocalTime.of(9, 0))
                .patient(patient).dentist(activeDentist).build();

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));
        when(clinicSettingsRepository.findAll()).thenReturn(List.of(settings));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(999L);
            }
            return saved;
        });
        when(appointmentMapper.toResponse(any(Appointment.class))).thenReturn(AppointmentResponse.builder().build());

        appointmentService.cancel(1L, adminAuth());

        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        verify(kafkaTemplate).send(eq(KafkaTopics.APPOINTMENT_CANCELLED), eq("1"), any());
    }

    @Test
    void cancel_withinDeadlineWindow_throwsIllegalArgument() {
        Appointment appt = Appointment.builder().id(1L).status(AppointmentStatus.PENDING)
                .appointmentDate(TODAY).startTime(LocalTime.of(11, 0))
                .patient(patient).dentist(activeDentist).build();

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));
        when(clinicSettingsRepository.findAll()).thenReturn(List.of(settings));

        assertThatThrownBy(() -> appointmentService.cancel(1L, adminAuth()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void cancel_alreadyCompleted_throwsIllegalArgument() {
        Appointment appt = Appointment.builder().id(1L).status(AppointmentStatus.COMPLETED)
                .appointmentDate(TODAY.plusDays(5)).startTime(LocalTime.of(9, 0))
                .patient(patient).dentist(activeDentist).build();

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

        assertThatThrownBy(() -> appointmentService.cancel(1L, adminAuth()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void cancel_nonOwnerPatient_throwsAccessDenied() {
        Appointment appt = Appointment.builder().id(1L).status(AppointmentStatus.PENDING)
                .appointmentDate(TODAY.plusDays(5)).startTime(LocalTime.of(9, 0))
                .patient(patient).dentist(activeDentist).build();

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

        assertThatThrownBy(() -> appointmentService.cancel(1L, patientAuth("someone-else@example.com")))
                .isInstanceOf(AccessDeniedException.class);
        verify(appointmentRepository, never()).save(any());
    }

    // ---------- confirm() ----------

    @Test
    void confirm_pendingAppointment_byAdmin_succeeds() {
        Appointment appt = Appointment.builder().id(1L).status(AppointmentStatus.PENDING)
                .patient(patient).dentist(activeDentist).build();

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(999L);
            }
            return saved;
        });
        when(appointmentMapper.toResponse(any(Appointment.class))).thenReturn(AppointmentResponse.builder().build());

        appointmentService.confirm(1L, adminAuth());

        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void confirm_byReceptionist_succeeds() {
        Appointment appt = Appointment.builder().id(1L).status(AppointmentStatus.PENDING)
                .patient(patient).dentist(activeDentist).build();

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(999L);
            }
            return saved;
        });
        when(appointmentMapper.toResponse(any(Appointment.class))).thenReturn(AppointmentResponse.builder().build());

        appointmentService.confirm(1L, receptionistAuth());

        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void confirm_byAssignedDentist_succeeds() {
        Appointment appt = Appointment.builder().id(1L).status(AppointmentStatus.PENDING)
                .patient(patient).dentist(activeDentist).build();

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(999L);
            }
            return saved;
        });
        when(appointmentMapper.toResponse(any(Appointment.class))).thenReturn(AppointmentResponse.builder().build());

        appointmentService.confirm(1L, dentistAuth(activeDentist.getUser().getEmail()));

        verify(appointmentRepository).save(any(Appointment.class));
    }

    @Test
    void confirm_byUnassignedDentist_throwsAccessDenied() {
        Appointment appt = Appointment.builder().id(1L).status(AppointmentStatus.PENDING)
                .patient(patient).dentist(activeDentist).build();

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

        assertThatThrownBy(() -> appointmentService.confirm(1L, dentistAuth("other-dentist@example.com")))
                .isInstanceOf(AccessDeniedException.class);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void confirm_byPatient_throwsAccessDenied() {
        Appointment appt = Appointment.builder().id(1L).status(AppointmentStatus.PENDING)
                .patient(patient).dentist(activeDentist).build();

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

        assertThatThrownBy(() -> appointmentService.confirm(1L, patientAuth(PATIENT_EMAIL)))
                .isInstanceOf(AccessDeniedException.class);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void confirm_notPending_throwsIllegalArgument() {
        Appointment appt = Appointment.builder().id(1L).status(AppointmentStatus.CANCELLED)
                .patient(patient).dentist(activeDentist).build();

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

        assertThatThrownBy(() -> appointmentService.confirm(1L, adminAuth()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(appointmentRepository, never()).save(any());
    }

    // ---------- complete() ----------

    @Test
    void complete_confirmedAppointment_succeeds() {
        Appointment appt = Appointment.builder().id(1L).status(AppointmentStatus.CONFIRMED)
                .patient(patient).dentist(activeDentist).build();

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(999L);
            }
            return saved;
        });
        when(appointmentMapper.toResponse(any(Appointment.class))).thenReturn(AppointmentResponse.builder().build());

        appointmentService.complete(1L, adminAuth());

        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    void complete_stillPending_throwsIllegalArgument() {
        Appointment appt = Appointment.builder().id(1L).status(AppointmentStatus.PENDING)
                .patient(patient).dentist(activeDentist).build();

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

        assertThatThrownBy(() -> appointmentService.complete(1L, adminAuth()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(appointmentRepository, never()).save(any());
    }

    // ---------- markNoShow() ----------

    @Test
    void markNoShow_confirmedAppointment_succeeds() {
        Appointment appt = Appointment.builder().id(1L).status(AppointmentStatus.CONFIRMED)
                .patient(patient).dentist(activeDentist).build();

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(999L);
            }
            return saved;
        });
        when(appointmentMapper.toResponse(any(Appointment.class))).thenReturn(AppointmentResponse.builder().build());

        appointmentService.markNoShow(1L, receptionistAuth());

        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);
    }

    @Test
    void markNoShow_alreadyCompleted_throwsIllegalArgument() {
        Appointment appt = Appointment.builder().id(1L).status(AppointmentStatus.COMPLETED)
                .patient(patient).dentist(activeDentist).build();

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

        assertThatThrownBy(() -> appointmentService.markNoShow(1L, adminAuth()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(appointmentRepository, never()).save(any());
    }

    // ---------- findById() với RECEPTIONIST ----------

    @Test
    void findById_byReceptionist_notOwner_succeeds() {
        Appointment appt = Appointment.builder().id(1L).status(AppointmentStatus.PENDING)
                .patient(patient).dentist(activeDentist).build();

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));
        when(appointmentMapper.toResponse(appt)).thenReturn(AppointmentResponse.builder().build());

        appointmentService.findById(1L, receptionistAuth());
    }
}
