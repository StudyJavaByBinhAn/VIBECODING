package com.vibecode.antijob.service;

import com.vibecode.antijob.dto.ClinicSettingsResponse;
import com.vibecode.antijob.dto.UpdateClinicSettingsRequest;
import com.vibecode.antijob.entity.ClinicSettings;
import com.vibecode.antijob.mapper.ClinicSettingsMapper;
import com.vibecode.antijob.repository.ClinicSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicSettingsAdminServiceTest {

    @Mock
    private ClinicSettingsRepository clinicSettingsRepository;
    @Mock
    private ClinicSettingsMapper clinicSettingsMapper;

    private ClinicSettingsAdminService clinicSettingsAdminService;

    private ClinicSettings settings;

    @BeforeEach
    void setUp() {
        clinicSettingsAdminService = new ClinicSettingsAdminService(clinicSettingsRepository, clinicSettingsMapper);
        settings = ClinicSettings.builder().id(1L).clinicName("Nha Khoa VibeCode")
                .openTime(LocalTime.of(8, 0)).closeTime(LocalTime.of(17, 0)).build();
    }

    @Test
    void get_existingSettings_returnsResponse() {
        when(clinicSettingsRepository.findAll()).thenReturn(List.of(settings));
        when(clinicSettingsMapper.toResponse(settings)).thenReturn(ClinicSettingsResponse.builder().id(1L).build());

        ClinicSettingsResponse response = clinicSettingsAdminService.get();

        assertThat(response.getId()).isEqualTo(1L);
    }

    @Test
    void get_missingSettings_throwsIllegalState() {
        when(clinicSettingsRepository.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> clinicSettingsAdminService.get())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void update_happyPath_updatesAllFields() {
        UpdateClinicSettingsRequest req = new UpdateClinicSettingsRequest();
        req.setClinicName("Clinic mới");
        req.setOpenTime(LocalTime.of(7, 30));
        req.setCloseTime(LocalTime.of(18, 0));
        req.setSlotDurationMinutes(45);
        req.setMaxAdvanceBookingDays(60);
        req.setBufferMinutes(15);
        req.setBreakStart(LocalTime.of(12, 30));
        req.setBreakEnd(LocalTime.of(13, 30));
        req.setCancelBeforeHours(24);
        req.setMaxPendingAppointments(5);

        when(clinicSettingsRepository.findAll()).thenReturn(List.of(settings));
        when(clinicSettingsRepository.save(any(ClinicSettings.class))).thenAnswer(inv -> inv.getArgument(0));
        when(clinicSettingsMapper.toResponse(any(ClinicSettings.class))).thenReturn(ClinicSettingsResponse.builder().build());

        clinicSettingsAdminService.update(req);

        ArgumentCaptor<ClinicSettings> captor = ArgumentCaptor.forClass(ClinicSettings.class);
        verify(clinicSettingsRepository).save(captor.capture());
        ClinicSettings saved = captor.getValue();
        assertThat(saved.getClinicName()).isEqualTo("Clinic mới");
        assertThat(saved.getSlotDurationMinutes()).isEqualTo(45);
        assertThat(saved.getMaxPendingAppointments()).isEqualTo(5);
    }
}
