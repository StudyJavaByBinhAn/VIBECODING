package com.vibecode.antijob.service;

import com.vibecode.antijob.entity.Appointment;
import com.vibecode.antijob.entity.ClinicSettings;
import com.vibecode.antijob.entity.WorkSchedule;
import com.vibecode.antijob.enums.AppointmentStatus;
import com.vibecode.antijob.repository.AppointmentRepository;
import com.vibecode.antijob.repository.ClinicSettingsRepository;
import com.vibecode.antijob.repository.WorkScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlotServiceTest {

    // "Now" fixed tại 2026-07-15 (Thứ Tư) 10:00
    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 15);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            TODAY.atTime(10, 0).atZone(ZONE).toInstant(), ZONE);

    @Mock
    private WorkScheduleRepository workScheduleRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private ClinicSettingsRepository clinicSettingsRepository;

    private SlotService slotService;

    @BeforeEach
    void setUp() {
        slotService = new SlotService(workScheduleRepository, appointmentRepository, clinicSettingsRepository, FIXED_CLOCK);
    }

    private WorkSchedule schedule(LocalTime start, LocalTime end) {
        return WorkSchedule.builder().startTime(start).endTime(end).active(true).build();
    }

    private ClinicSettings settings(int slotMinutes, int bufferMinutes, LocalTime breakStart, LocalTime breakEnd, LocalTime closeTime) {
        return ClinicSettings.builder()
                .slotDurationMinutes(slotMinutes)
                .bufferMinutes(bufferMinutes)
                .breakStart(breakStart)
                .breakEnd(breakEnd)
                .closeTime(closeTime)
                .build();
    }

    private Appointment booking(LocalTime start, LocalTime end) {
        return Appointment.builder().startTime(start).endTime(end).status(AppointmentStatus.PENDING).build();
    }

    @Test
    void noActiveWorkSchedule_returnsEmpty() {
        when(workScheduleRepository.findByDentistIdAndDayOfWeek(eq(1L), any(DayOfWeek.class)))
                .thenReturn(Optional.empty());

        List<LocalTime> slots = slotService.getAvailableSlots(1L, TODAY.plusDays(1));

        assertThat(slots).isEmpty();
    }

    @Test
    void normalCase_noBookingsNoBufferNoBreak_returnsAllSlotsWithinCloseTime() {
        LocalDate future = TODAY.plusDays(1);
        when(workScheduleRepository.findByDentistIdAndDayOfWeek(eq(1L), any(DayOfWeek.class)))
                .thenReturn(Optional.of(schedule(LocalTime.of(8, 0), LocalTime.of(17, 0))));
        when(clinicSettingsRepository.findAll())
                .thenReturn(List.of(settings(30, 0, null, null, LocalTime.of(17, 0))));
        when(appointmentRepository.findByDentistIdAndAppointmentDateAndStatusNot(1L, future, AppointmentStatus.CANCELLED))
                .thenReturn(List.of());

        List<LocalTime> slots = slotService.getAvailableSlots(1L, future);

        assertThat(slots).hasSize(18); // 08:00 -> 16:30, mỗi 30 phút
        assertThat(slots).contains(LocalTime.of(8, 0), LocalTime.of(16, 30));
    }

    @Test
    void oneBooking_noBuffer_excludesOnlyOverlappingSlot() {
        LocalDate future = TODAY.plusDays(1);
        when(workScheduleRepository.findByDentistIdAndDayOfWeek(eq(1L), any(DayOfWeek.class)))
                .thenReturn(Optional.of(schedule(LocalTime.of(8, 0), LocalTime.of(11, 0))));
        when(clinicSettingsRepository.findAll())
                .thenReturn(List.of(settings(30, 0, null, null, LocalTime.of(17, 0))));
        when(appointmentRepository.findByDentistIdAndAppointmentDateAndStatusNot(1L, future, AppointmentStatus.CANCELLED))
                .thenReturn(List.of(booking(LocalTime.of(9, 0), LocalTime.of(9, 30))));

        List<LocalTime> slots = slotService.getAvailableSlots(1L, future);

        assertThat(slots).doesNotContain(LocalTime.of(9, 0));
        assertThat(slots).contains(LocalTime.of(8, 30), LocalTime.of(9, 30));
    }

    @Test
    void bufferMinutes_excludesAdjacentSlotsOnBothSides() {
        LocalDate future = TODAY.plusDays(1);
        when(workScheduleRepository.findByDentistIdAndDayOfWeek(eq(1L), any(DayOfWeek.class)))
                .thenReturn(Optional.of(schedule(LocalTime.of(8, 0), LocalTime.of(11, 0))));
        when(clinicSettingsRepository.findAll())
                .thenReturn(List.of(settings(30, 10, null, null, LocalTime.of(17, 0))));
        when(appointmentRepository.findByDentistIdAndAppointmentDateAndStatusNot(1L, future, AppointmentStatus.CANCELLED))
                .thenReturn(List.of(booking(LocalTime.of(9, 0), LocalTime.of(9, 30))));

        List<LocalTime> slots = slotService.getAvailableSlots(1L, future);

        assertThat(slots).doesNotContain(LocalTime.of(8, 30), LocalTime.of(9, 0), LocalTime.of(9, 30));
        assertThat(slots).contains(LocalTime.of(10, 0));
    }

    @Test
    void breakWindow_excludesSlotsInsideBreak() {
        LocalDate future = TODAY.plusDays(1);
        when(workScheduleRepository.findByDentistIdAndDayOfWeek(eq(1L), any(DayOfWeek.class)))
                .thenReturn(Optional.of(schedule(LocalTime.of(11, 0), LocalTime.of(14, 0))));
        when(clinicSettingsRepository.findAll())
                .thenReturn(List.of(settings(30, 0, LocalTime.of(12, 0), LocalTime.of(13, 0), LocalTime.of(17, 0))));
        when(appointmentRepository.findByDentistIdAndAppointmentDateAndStatusNot(1L, future, AppointmentStatus.CANCELLED))
                .thenReturn(List.of());

        List<LocalTime> slots = slotService.getAvailableSlots(1L, future);

        assertThat(slots).doesNotContain(LocalTime.of(12, 0), LocalTime.of(12, 30));
        assertThat(slots).contains(LocalTime.of(11, 30), LocalTime.of(13, 0));
    }

    @Test
    void noBreakConfigured_doesNotExcludeAnySlot() {
        LocalDate future = TODAY.plusDays(1);
        when(workScheduleRepository.findByDentistIdAndDayOfWeek(eq(1L), any(DayOfWeek.class)))
                .thenReturn(Optional.of(schedule(LocalTime.of(11, 0), LocalTime.of(14, 0))));
        when(clinicSettingsRepository.findAll())
                .thenReturn(List.of(settings(30, 0, null, null, LocalTime.of(17, 0))));
        when(appointmentRepository.findByDentistIdAndAppointmentDateAndStatusNot(1L, future, AppointmentStatus.CANCELLED))
                .thenReturn(List.of());

        List<LocalTime> slots = slotService.getAvailableSlots(1L, future);

        assertThat(slots).contains(LocalTime.of(12, 0), LocalTime.of(12, 30));
    }

    @Test
    void slotPastCloseTime_isCappedEvenIfWorkScheduleAllowsLater() {
        LocalDate future = TODAY.plusDays(1);
        when(workScheduleRepository.findByDentistIdAndDayOfWeek(eq(1L), any(DayOfWeek.class)))
                .thenReturn(Optional.of(schedule(LocalTime.of(16, 0), LocalTime.of(18, 0))));
        when(clinicSettingsRepository.findAll())
                .thenReturn(List.of(settings(30, 0, null, null, LocalTime.of(17, 0))));
        when(appointmentRepository.findByDentistIdAndAppointmentDateAndStatusNot(1L, future, AppointmentStatus.CANCELLED))
                .thenReturn(List.of());

        List<LocalTime> slots = slotService.getAvailableSlots(1L, future);

        assertThat(slots).containsExactly(LocalTime.of(16, 0), LocalTime.of(16, 30));
    }

    @Test
    void today_excludesSlotsBeforeCurrentTime() {
        when(workScheduleRepository.findByDentistIdAndDayOfWeek(eq(1L), any(DayOfWeek.class)))
                .thenReturn(Optional.of(schedule(LocalTime.of(8, 0), LocalTime.of(11, 0))));
        when(clinicSettingsRepository.findAll())
                .thenReturn(List.of(settings(30, 0, null, null, LocalTime.of(17, 0))));
        when(appointmentRepository.findByDentistIdAndAppointmentDateAndStatusNot(1L, TODAY, AppointmentStatus.CANCELLED))
                .thenReturn(List.of());

        // Clock cố định 10:00 hôm nay
        List<LocalTime> slots = slotService.getAvailableSlots(1L, TODAY);

        assertThat(slots).doesNotContain(LocalTime.of(8, 0), LocalTime.of(9, 30));
        assertThat(slots).contains(LocalTime.of(10, 0), LocalTime.of(10, 30));
    }

    @Test
    void futureDate_doesNotApplyPastSlotFilterRegardlessOfClock() {
        LocalDate future = TODAY.plusDays(2);
        when(workScheduleRepository.findByDentistIdAndDayOfWeek(eq(1L), any(DayOfWeek.class)))
                .thenReturn(Optional.of(schedule(LocalTime.of(8, 0), LocalTime.of(9, 0))));
        when(clinicSettingsRepository.findAll())
                .thenReturn(List.of(settings(30, 0, null, null, LocalTime.of(17, 0))));
        when(appointmentRepository.findByDentistIdAndAppointmentDateAndStatusNot(1L, future, AppointmentStatus.CANCELLED))
                .thenReturn(List.of());

        List<LocalTime> slots = slotService.getAvailableSlots(1L, future);

        assertThat(slots).contains(LocalTime.of(8, 0), LocalTime.of(8, 30));
    }

    @Test
    void missingClinicSettings_fallsBackToDefaultSlotDurationAndNoBufferOrCap() {
        LocalDate future = TODAY.plusDays(1);
        when(workScheduleRepository.findByDentistIdAndDayOfWeek(eq(1L), any(DayOfWeek.class)))
                .thenReturn(Optional.of(schedule(LocalTime.of(8, 0), LocalTime.of(9, 0))));
        when(clinicSettingsRepository.findAll()).thenReturn(List.of());
        when(appointmentRepository.findByDentistIdAndAppointmentDateAndStatusNot(1L, future, AppointmentStatus.CANCELLED))
                .thenReturn(List.of());

        List<LocalTime> slots = slotService.getAvailableSlots(1L, future);

        assertThat(slots).containsExactly(LocalTime.of(8, 0), LocalTime.of(8, 30));
    }
}
