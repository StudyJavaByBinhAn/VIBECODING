package com.vibecode.antijob.repository;

import com.vibecode.antijob.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRepository extends JpaRepository<Patient, Long> {
}
