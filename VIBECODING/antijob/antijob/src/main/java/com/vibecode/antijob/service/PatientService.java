package com.vibecode.antijob.service;

import com.vibecode.antijob.dto.PatientResponse;
import com.vibecode.antijob.dto.UpdatePatientRequest;
import com.vibecode.antijob.entity.Patient;
import com.vibecode.antijob.mapper.PatientMapper;
import com.vibecode.antijob.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;

    @Transactional(readOnly = true)
    public PatientResponse getMe(String email) {
        return patientMapper.toResponse(getOrThrow(email));
    }

    @Transactional
    public PatientResponse updateMe(String email, UpdatePatientRequest req) {
        Patient patient = getOrThrow(email);
        patient.setPhone(req.getPhone());
        patient.setDateOfBirth(req.getDateOfBirth());
        patient.setGender(req.getGender());
        patient.setAddress(req.getAddress());
        patient.setMedicalHistory(req.getMedicalHistory());
        return patientMapper.toResponse(patientRepository.save(patient));
    }

    private Patient getOrThrow(String email) {
        return patientRepository.findByUserEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ bệnh nhân cho tài khoản này"));
    }
}
