package com.vibecode.customercare.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Model 1 patient : N staff — mỗi patient có đúng 1 conversation (unique theo patientEmail),
// bất kỳ staff nào (ADMIN/RECEPTIONIST/DENTIST) cũng xem/trả lời được, không phải 1:1 riêng
// từng cặp (quyết định đã chốt với user, xem plan file).
@Document(collection = "conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    private String id;

    @Indexed(unique = true)
    private String patientEmail;

    private Instant createdAt;
    private Instant lastMessageAt;
}
