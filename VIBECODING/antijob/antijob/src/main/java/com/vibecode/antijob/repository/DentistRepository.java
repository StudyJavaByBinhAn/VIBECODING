package com.vibecode.antijob.repository;

import com.vibecode.antijob.entity.Dentist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DentistRepository extends JpaRepository<Dentist, Long> {
}
