package com.vibecode.antijob.service;

import com.vibecode.antijob.dto.PatientResponse;
import com.vibecode.antijob.dto.UpdatePatientRequest;
import com.vibecode.antijob.entity.Patient;
import com.vibecode.antijob.entity.User;
import com.vibecode.antijob.enums.Gender;
import com.vibecode.antijob.mapper.PatientMapper;
import com.vibecode.antijob.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    private static final String EMAIL = "patient@example.com";

    @Mock
    private PatientRepository patientRepository;
    @Mock
    private PatientMapper patientMapper;

    private PatientService patientService;

    private Patient patient;

    @BeforeEach
    void setUp() {
        patientService = new PatientService(patientRepository, patientMapper);
        patient = Patient.builder().id(1L).user(User.builder().email(EMAIL).build())
                .fullName("Nguyen Van A").phone("0900000001").build();
    }

    @Test
    void getMe_existingProfile_returnsResponse() {
        when(patientRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(patient));
        when(patientMapper.toResponse(patient)).thenReturn(PatientResponse.builder().id(1L).build());

        PatientResponse response = patientService.getMe(EMAIL);

        assertThat(response.getId()).isEqualTo(1L);
    }

    @Test
    void getMe_noProfile_throwsIllegalArgument() {
        when(patientRepository.findByUserEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.getMe(EMAIL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateMe_happyPath_updatesEditableFieldsOnly() {
        UpdatePatientRequest req = new UpdatePatientRequest();
        req.setPhone("0911111111");
        req.setDateOfBirth(LocalDate.of(2000, 1, 1));
        req.setGender(Gender.FEMALE);
        req.setAddress("123 Test St");
        req.setMedicalHistory("Dị ứng penicillin");

        when(patientRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(patient));
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));
        when(patientMapper.toResponse(any(Patient.class))).thenReturn(PatientResponse.builder().build());

        patientService.updateMe(EMAIL, req);

        ArgumentCaptor<Patient> captor = ArgumentCaptor.forClass(Patient.class);
        verify(patientRepository).save(captor.capture());
        Patient saved = captor.getValue();
        assertThat(saved.getPhone()).isEqualTo("0911111111");
        assertThat(saved.getDateOfBirth()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(saved.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(saved.getAddress()).isEqualTo("123 Test St");
        assertThat(saved.getMedicalHistory()).isEqualTo("Dị ứng penicillin");
        assertThat(saved.getFullName()).isEqualTo("Nguyen Van A");
    }

    @Test
    void updateMe_noProfile_throwsIllegalArgument() {
        UpdatePatientRequest req = new UpdatePatientRequest();
        req.setPhone("0911111111");

        when(patientRepository.findByUserEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.updateMe(EMAIL, req))
                .isInstanceOf(IllegalArgumentException.class);
        verify(patientRepository, never()).save(any());
    }
}
