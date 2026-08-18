package com.vibecode.customercare.service;

import com.vibecode.customercare.document.Conversation;
import com.vibecode.customercare.document.Message;
import com.vibecode.customercare.dto.ChatMessageResponse;
import com.vibecode.customercare.dto.ChatSendRequest;
import com.vibecode.customercare.dto.ConversationResponse;
import com.vibecode.customercare.dto.PageResponse;
import com.vibecode.customercare.event.ChatMessageSentPayload;
import com.vibecode.customercare.event.DomainEvent;
import com.vibecode.customercare.event.KafkaTopics;
import com.vibecode.customercare.repository.ConversationRepository;
import com.vibecode.customercare.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Clock clock;

    // Gọi từ STOMP handler (ChatStompController). senderEmail/senderRole lấy từ Principal đã
    // gắn ở StompAuthChannelInterceptor lúc CONNECT, không tin dữ liệu client tự khai trong payload.
    public void sendMessage(String senderEmail, String senderRole, ChatSendRequest req) {
        if (req.getContent() == null || req.getContent().isBlank()) {
            throw new IllegalArgumentException("Nội dung tin nhắn không được để trống");
        }
        Conversation conversation = resolveConversation(senderEmail, senderRole, req.getConversationId());

        Message message = messageRepository.save(Message.builder()
                .conversationId(conversation.getId())
                .senderEmail(senderEmail)
                .senderRole(senderRole)
                .content(req.getContent())
                .sentAt(Instant.now(clock))
                .build());

        conversation.setLastMessageAt(message.getSentAt());
        conversationRepository.save(conversation);

        ChatMessageResponse response = toResponse(message);
        messagingTemplate.convertAndSend("/topic/conversations/" + conversation.getId(), response);

        kafkaTemplate.send(KafkaTopics.CHAT_MESSAGE_SENT, conversation.getId(), DomainEvent.<Object>builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(KafkaTopics.CHAT_MESSAGE_SENT)
                .version(1)
                .occurredAt(Instant.now(clock))
                .data(ChatMessageSentPayload.builder()
                        .conversationId(conversation.getId())
                        .patientEmail(conversation.getPatientEmail())
                        .senderEmail(senderEmail)
                        .senderRole(senderRole)
                        .content(req.getContent())
                        .sentAt(message.getSentAt())
                        .build())
                .build());
    }

    // PATIENT: đúng 1 conversation, tự tạo ở tin nhắn đầu tiên nếu chưa có.
    // STAFF (ADMIN/RECEPTIONIST/DENTIST): phải chỉ định conversationId có sẵn — không tự tạo hộ.
    private Conversation resolveConversation(String senderEmail, String senderRole, String conversationId) {
        if ("PATIENT".equals(senderRole)) {
            return conversationRepository.findByPatientEmail(senderEmail)
                    .orElseGet(() -> conversationRepository.save(Conversation.builder()
                            .patientEmail(senderEmail)
                            .createdAt(Instant.now(clock))
                            .lastMessageAt(Instant.now(clock))
                            .build()));
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("Staff phải chỉ định conversationId khi gửi tin");
        }
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy conversation id=" + conversationId));
    }

    public PageResponse<ConversationResponse> listConversations(Authentication authentication, Pageable pageable) {
        if (isStaff(authentication)) {
            Page<Conversation> page = conversationRepository.findAllByOrderByLastMessageAtDesc(pageable);
            return toPageResponse(page.map(this::toResponse));
        }

        String email = authentication.getName();
        List<ConversationResponse> mine = conversationRepository.findByPatientEmail(email)
                .map(c -> List.of(toResponse(c)))
                .orElse(List.of());
        return PageResponse.<ConversationResponse>builder()
                .content(mine).page(0).size(mine.size())
                .totalElements(mine.size()).totalPages(mine.isEmpty() ? 0 : 1)
                .build();
    }

    public PageResponse<ChatMessageResponse> getMessages(String conversationId, Authentication authentication, Pageable pageable) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy conversation id=" + conversationId));

        if (!isStaff(authentication) && !conversation.getPatientEmail().equals(authentication.getName())) {
            throw new AccessDeniedException("Không có quyền xem conversation id=" + conversationId);
        }

        Page<Message> page = messageRepository.findByConversationIdOrderBySentAtDesc(conversationId, pageable);
        return toPageResponse(page.map(this::toResponse));
    }

    private boolean isStaff(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_RECEPTIONIST") || a.equals("ROLE_DENTIST"));
    }

    private ConversationResponse toResponse(Conversation c) {
        return ConversationResponse.builder()
                .id(c.getId()).patientEmail(c.getPatientEmail())
                .createdAt(c.getCreatedAt()).lastMessageAt(c.getLastMessageAt())
                .build();
    }

    private ChatMessageResponse toResponse(Message m) {
        return ChatMessageResponse.builder()
                .id(m.getId()).conversationId(m.getConversationId())
                .senderEmail(m.getSenderEmail()).senderRole(m.getSenderRole())
                .content(m.getContent()).sentAt(m.getSentAt())
                .build();
    }

    private <T> PageResponse<T> toPageResponse(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent()).page(page.getNumber()).size(page.getSize())
                .totalElements(page.getTotalElements()).totalPages(page.getTotalPages())
                .build();
    }
}
