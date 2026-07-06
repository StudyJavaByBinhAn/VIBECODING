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

        @Cacheable(cacheNames = "slots", key = "#dentistId + ':' + #date")
        public List<LocalTime> getAvailableSlots(Long dentistId, LocalDate date) {
                WorkSchedule schedule = workScheduleRepository
                                .findByDentistIdAndDayOfWeek(dentistId, date.getDayOfWeek())
                                .filter(WorkSchedule::isActive)
                                .orElse(null);

                if (schedule == null)
                        return List.of();

                int slotMinutes = clinicSettingsRepository.findAll().stream()
                                .findFirst()
                                .map(ClinicSettings::getSlotDurationMinutes)
                                .orElse(30);

                List<Appointment> booked = appointmentRepository
                                .findByDentistIdAndAppointmentDateAndStatusNot(dentistId, date,
                                                AppointmentStatus.CANCELLED);

                List<LocalTime> slots = new ArrayList<>();
                LocalTime current = schedule.getStartTime();

                while (current.plusMinutes(slotMinutes).compareTo(schedule.getEndTime()) <= 0) {
                        final LocalTime slotStart = current;
                        final LocalTime slotEnd = current.plusMinutes(slotMinutes);

                        boolean isBooked = booked.stream().anyMatch(
                                        a -> a.getStartTime().isBefore(slotEnd) && a.getEndTime().isAfter(slotStart));

                        if (!isBooked)
                                slots.add(slotStart);
                        current = slotEnd;
                }

                return slots;
        }
}
