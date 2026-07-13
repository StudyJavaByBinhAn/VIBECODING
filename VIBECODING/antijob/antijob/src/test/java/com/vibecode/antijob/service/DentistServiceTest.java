package com.vibecode.antijob.service;

import com.vibecode.antijob.dto.DentistResponse;
import com.vibecode.antijob.dto.UpdateDentistRequest;
import com.vibecode.antijob.entity.Dentist;
import com.vibecode.antijob.mapper.DentistMapper;
import com.vibecode.antijob.repository.DentistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DentistServiceTest {

    @Mock
    private DentistRepository dentistRepository;
    @Mock
    private DentistMapper dentistMapper;

    private DentistService dentistService;

    private Dentist dentist;

    @BeforeEach
    void setUp() {
        dentistService = new DentistService(dentistRepository, dentistMapper);
        dentist = Dentist.builder().id(1L).fullName("BS A").phone("0900000001")
                .licenseNumber("DN-001").active(true).build();
    }

    private UpdateDentistRequest updateRequest(String licenseNumber) {
        UpdateDentistRequest req = new UpdateDentistRequest();
        req.setFullName("BS A Updated");
        req.setPhone("0900000099");
        req.setSpecialization("Chỉnh nha");
        req.setLicenseNumber(licenseNumber);
        req.setBio("Cập nhật bio");
        return req;
    }

    @Test
    void update_happyPath_updatesFields() {
        UpdateDentistRequest req = updateRequest("DN-001");

        when(dentistRepository.findById(1L)).thenReturn(Optional.of(dentist));
        when(dentistRepository.existsByLicenseNumberAndIdNot("DN-001", 1L)).thenReturn(false);
        when(dentistRepository.save(any(Dentist.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dentistMapper.toResponse(any(Dentist.class))).thenReturn(DentistResponse.builder().build());

        dentistService.update(1L, req);

        ArgumentCaptor<Dentist> captor = ArgumentCaptor.forClass(Dentist.class);
        verify(dentistRepository).save(captor.capture());
        assertThat(captor.getValue().getFullName()).isEqualTo("BS A Updated");
        assertThat(captor.getValue().getPhone()).isEqualTo("0900000099");
    }

    @Test
    void update_duplicateLicenseNumber_throwsIllegalArgument() {
        UpdateDentistRequest req = updateRequest("DN-999");

        when(dentistRepository.findById(1L)).thenReturn(Optional.of(dentist));
        when(dentistRepository.existsByLicenseNumberAndIdNot("DN-999", 1L)).thenReturn(true);

        assertThatThrownBy(() -> dentistService.update(1L, req))
                .isInstanceOf(IllegalArgumentException.class);
        verify(dentistRepository, never()).save(any());
    }

    @Test
    void update_notFound_throwsIllegalArgument() {
        UpdateDentistRequest req = updateRequest("DN-001");

        when(dentistRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dentistService.update(1L, req))
                .isInstanceOf(IllegalArgumentException.class);
        verify(dentistRepository, never()).save(any());
    }
}
