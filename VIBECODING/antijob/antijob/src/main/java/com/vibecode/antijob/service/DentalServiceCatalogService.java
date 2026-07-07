package com.vibecode.antijob.service;

import com.vibecode.antijob.dto.DentalServiceResponse;
import com.vibecode.antijob.entity.DentalService;
import com.vibecode.antijob.mapper.DentalServiceMapper;
import com.vibecode.antijob.repository.DentalServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DentalServiceCatalogService {

    private final DentalServiceRepository dentalServiceRepository;
    private final DentalServiceMapper dentalServiceMapper;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "services-active", key = "'all'")
    public List<DentalServiceResponse> findAllActive() {
        return dentalServiceRepository.findByActiveTrue().stream()
                .map(dentalServiceMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(cacheNames = "services-active", allEntries = true)
    public DentalServiceResponse updateActive(Long id, boolean active) {
        DentalService service = dentalServiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy dịch vụ id=" + id));
        service.setActive(active);
        return dentalServiceMapper.toResponse(dentalServiceRepository.save(service));
    }
}
