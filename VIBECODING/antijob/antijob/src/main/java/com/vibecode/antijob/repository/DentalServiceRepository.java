package com.vibecode.antijob.repository;

import com.vibecode.antijob.entity.DentalService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DentalServiceRepository extends JpaRepository<DentalService, Long> {
    List<DentalService> findByActiveTrue();
}
