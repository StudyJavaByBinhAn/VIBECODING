package com.vibecode.antijob.service;

import com.vibecode.antijob.dto.DentistResponse;
import com.vibecode.antijob.dto.UpdateDentistRequest;
import com.vibecode.antijob.entity.Dentist;
import com.vibecode.antijob.mapper.DentistMapper;
import com.vibecode.antijob.repository.DentistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DentistService {

    private final DentistRepository dentistRepository;
    private final DentistMapper dentistMapper;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "dentists-active", key = "'all'")
    public List<DentistResponse> findAllActive() {
        return dentistRepository.findByActiveTrue().stream()
                .map(dentistMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(cacheNames = "dentists-active", allEntries = true)
    public DentistResponse updateActive(Long id, boolean active) {
        Dentist dentist = dentistRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bác sĩ id=" + id));
        dentist.setActive(active);
        return dentistMapper.toResponse(dentistRepository.save(dentist));
    }

    @Transactional
    @CacheEvict(cacheNames = "dentists-active", allEntries = true)
    public DentistResponse update(Long id, UpdateDentistRequest req) {
        Dentist dentist = dentistRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bác sĩ id=" + id));

        if (dentistRepository.existsByLicenseNumberAndIdNot(req.getLicenseNumber(), id)) {
            throw new IllegalArgumentException("Số chứng chỉ hành nghề đã được sử dụng: " + req.getLicenseNumber());
        }

        dentist.setFullName(req.getFullName());
        dentist.setPhone(req.getPhone());
        dentist.setSpecialization(req.getSpecialization());
        dentist.setLicenseNumber(req.getLicenseNumber());
        dentist.setBio(req.getBio());

        return dentistMapper.toResponse(dentistRepository.save(dentist));
    }
}
