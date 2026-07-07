package com.vibecode.antijob.service;

import com.vibecode.antijob.dto.DentistResponse;
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
}
