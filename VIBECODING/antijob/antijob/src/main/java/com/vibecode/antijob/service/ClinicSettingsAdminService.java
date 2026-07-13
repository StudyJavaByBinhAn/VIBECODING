package com.vibecode.antijob.service;

import com.vibecode.antijob.dto.ClinicSettingsResponse;
import com.vibecode.antijob.dto.UpdateClinicSettingsRequest;
import com.vibecode.antijob.entity.ClinicSettings;
import com.vibecode.antijob.mapper.ClinicSettingsMapper;
import com.vibecode.antijob.repository.ClinicSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClinicSettingsAdminService {

    private final ClinicSettingsRepository clinicSettingsRepository;
    private final ClinicSettingsMapper clinicSettingsMapper;

    @Transactional(readOnly = true)
    public ClinicSettingsResponse get() {
        return clinicSettingsMapper.toResponse(getOrThrow());
    }

    @Transactional
    @CacheEvict(cacheNames = "slots", allEntries = true)
    public ClinicSettingsResponse update(UpdateClinicSettingsRequest req) {
        ClinicSettings settings = getOrThrow();
        settings.setClinicName(req.getClinicName());
        settings.setOpenTime(req.getOpenTime());
        settings.setCloseTime(req.getCloseTime());
        settings.setSlotDurationMinutes(req.getSlotDurationMinutes());
        settings.setMaxAdvanceBookingDays(req.getMaxAdvanceBookingDays());
        settings.setBufferMinutes(req.getBufferMinutes());
        settings.setBreakStart(req.getBreakStart());
        settings.setBreakEnd(req.getBreakEnd());
        settings.setCancelBeforeHours(req.getCancelBeforeHours());
        settings.setMaxPendingAppointments(req.getMaxPendingAppointments());
        return clinicSettingsMapper.toResponse(clinicSettingsRepository.save(settings));
    }

    private ClinicSettings getOrThrow() {
        return clinicSettingsRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Chưa cấu hình clinic_settings"));
    }
}
