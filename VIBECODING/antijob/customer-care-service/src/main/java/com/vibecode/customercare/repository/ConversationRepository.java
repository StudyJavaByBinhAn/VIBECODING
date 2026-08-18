package com.vibecode.customercare.repository;

import com.vibecode.customercare.document.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ConversationRepository extends MongoRepository<Conversation, String> {

    Optional<Conversation> findByPatientEmail(String patientEmail);

    Page<Conversation> findAllByOrderByLastMessageAtDesc(Pageable pageable);
}
