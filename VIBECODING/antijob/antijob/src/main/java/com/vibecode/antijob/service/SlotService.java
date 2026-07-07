package com.vibecode.antijob.service;

import com.vibecode.antijob.entity.Appointment;
import com.vibecode.antijob.entity.ClinicSettings;
import com.vibecode.antijob.entity.WorkSchedule;
import com.vibecode.antijob.enums.AppointmentStatus;
import com.vibecode.antijob.repository.AppointmentRepository;
import com.vibecode.antijob.repository.ClinicSettingsRepository;
import com.vibecode.antijob.repository.WorkScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SlotService {

        private final WorkScheduleRepository workScheduleRepository;
        private final AppointmentRepository appointmentRepository;
        private final ClinicSettingsRepository clinicSettingsRepository;
        private final Clock clock;

        @Cacheable(cacheNames = "slots", key = "#dentistId + ':' + #date")
        public List<LocalTime> getAvailableSlots(Long dentistId, LocalDate date) {
                WorkSchedule schedule = workScheduleRepository
                                .findByDentistIdAndDayOfWeek(dentistId, date.getDayOfWeek())
                                .filter(WorkSchedule::isActive)
                                .orElse(null);

                if (schedule == null)
                        return List.of();

                ClinicSettings settings = clinicSettingsRepository.findAll().stream().findFirst().orElse(null);
                int slotMinutes = settings != null ? settings.getSlotDurationMinutes() : 30;
                int bufferMinutes = settings != null ? settings.getBufferMinutes() : 0;
                LocalTime breakStart = settings != null ? settings.getBreakStart() : null;
                LocalTime breakEnd = settings != null ? settings.getBreakEnd() : null;
                LocalTime closeTime = settings != null ? settings.getCloseTime() : null;

                List<Appointment> booked = appointmentRepository
                                .findByDentistIdAndAppointmentDateAndStatusNot(dentistId, date,
                                                AppointmentStatus.CANCELLED);

                boolean isToday = date.isEqual(LocalDate.now(clock));
                LocalTime now = LocalTime.now(clock);

                List<LocalTime> slots = new ArrayList<>();
                LocalTime current = schedule.getStartTime();

                while (current.plusMinutes(slotMinutes).compareTo(schedule.getEndTime()) <= 0) {
                        final LocalTime slotStart = current;
                        final LocalTime slotEnd = current.plusMinutes(slotMinutes);
                        current = slotEnd;

                        if (closeTime != null && slotEnd.isAfter(closeTime))
                                continue;

                        if (breakStart != null && breakEnd != null
                                        && slotStart.isBefore(breakEnd) && slotEnd.isAfter(breakStart))
                                continue;

                        if (isToday && slotStart.isBefore(now))
                                continue;

                        boolean isBooked = booked.stream().anyMatch(
                                        a -> a.getStartTime().minusMinutes(bufferMinutes).isBefore(slotEnd)
                                                        && a.getEndTime().plusMinutes(bufferMinutes).isAfter(slotStart));

                        if (!isBooked)
                                slots.add(slotStart);
                }

                return slots;
        }
}
