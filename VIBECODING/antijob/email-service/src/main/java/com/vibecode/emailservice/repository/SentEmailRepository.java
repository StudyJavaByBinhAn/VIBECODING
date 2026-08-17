package com.vibecode.emailservice.repository;

import com.vibecode.emailservice.entity.SentEmail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SentEmailRepository extends JpaRepository<SentEmail, Long> {
}
