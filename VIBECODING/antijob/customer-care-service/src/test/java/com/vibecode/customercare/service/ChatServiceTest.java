package com.vibecode.customercare.service;

import com.vibecode.customercare.document.Conversation;
import com.vibecode.customercare.document.Message;
import com.vibecode.customercare.dto.ChatSendRequest;
import com.vibecode.customercare.dto.PageResponse;
import com.vibecode.customercare.repository.ConversationRepository;
import com.vibecode.customercare.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private ChatService chatService;

    private static final Instant FIXED_NOW = Instant.parse("2026-08-18T10:00:00Z");

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        chatService = new ChatService(conversationRepository, messageRepository, messagingTemplate, kafkaTemplate, clock);
    }

    @Test
    void sendMessage_patientFirstMessage_createsConversation_broadcasts_publishesKafka() {
        when(conversationRepository.findByPatientEmail("patient@dental.vn")).thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId("conv-1");
            }
            return c;
        });
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId("msg-1");
            return m;
        });

        ChatSendRequest req = new ChatSendRequest();
        req.setContent("Xin chào, tôi muốn hỏi về lịch hẹn");

        chatService.sendMessage("patient@dental.vn", "PATIENT", req);

        verify(messagingTemplate).convertAndSend(eq("/topic/conversations/conv-1"), any(Object.class));
        verify(kafkaTemplate).send(eq("chat.message-sent"), eq("conv-1"), any());
    }

    @Test
    void sendMessage_staffWithoutConversationId_throws() {
        ChatSendRequest req = new ChatSendRequest();
        req.setContent("Chào bạn");

        assertThatThrownBy(() -> chatService.sendMessage("staff@dental.vn", "RECEPTIONIST", req))
                .isInstanceOf(IllegalArgumentException.class);

        verify(messageRepository, never()).save(any());
    }

    @Test
    void sendMessage_blankContent_throws() {
        ChatSendRequest req = new ChatSendRequest();
        req.setContent("   ");

        assertThatThrownBy(() -> chatService.sendMessage("patient@dental.vn", "PATIENT", req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sendMessage_staffRepliesToExistingConversation_succeeds() {
        Conversation existing = Conversation.builder().id("conv-1").patientEmail("patient@dental.vn")
                .createdAt(FIXED_NOW).lastMessageAt(FIXED_NOW).build();
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(existing));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId("msg-2");
            return m;
        });

        ChatSendRequest req = new ChatSendRequest();
        req.setConversationId("conv-1");
        req.setContent("Chào bạn, lịch của bạn đã được xác nhận");

        chatService.sendMessage("staff@dental.vn", "RECEPTIONIST", req);

        verify(conversationRepository).save(existing);
        verify(messagingTemplate).convertAndSend(eq("/topic/conversations/conv-1"), any(Object.class));
    }

    @Test
    void getMessages_patientNotOwner_throwsAccessDenied() {
        Conversation conversation = Conversation.builder().id("conv-1").patientEmail("owner@dental.vn").build();
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(conversation));
        Authentication other = new UsernamePasswordAuthenticationToken("intruder@dental.vn", null,
                List.of(new SimpleGrantedAuthority("ROLE_PATIENT")));

        assertThatThrownBy(() -> chatService.getMessages("conv-1", other, PageRequest.of(0, 10)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getMessages_owningPatient_succeeds() {
        Conversation conversation = Conversation.builder().id("conv-1").patientEmail("patient@dental.vn").build();
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderBySentAtDesc(eq("conv-1"), any()))
                .thenReturn(new PageImpl<>(List.of()));
        Authentication owner = new UsernamePasswordAuthenticationToken("patient@dental.vn", null,
                List.of(new SimpleGrantedAuthority("ROLE_PATIENT")));

        PageResponse<?> result = chatService.getMessages("conv-1", owner, PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void listConversations_staff_returnsAll() {
        when(conversationRepository.findAllByOrderByLastMessageAtDesc(any()))
                .thenReturn(new PageImpl<>(List.of(
                        Conversation.builder().id("conv-1").patientEmail("a@dental.vn").build())));
        Authentication staff = new UsernamePasswordAuthenticationToken("staff@dental.vn", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        PageResponse<?> result = chatService.listConversations(staff, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void listConversations_patientWithNoConversationYet_returnsEmpty() {
        when(conversationRepository.findByPatientEmail("patient@dental.vn")).thenReturn(Optional.empty());
        Authentication patient = new UsernamePasswordAuthenticationToken("patient@dental.vn", null,
                List.of(new SimpleGrantedAuthority("ROLE_PATIENT")));

        PageResponse<?> result = chatService.listConversations(patient, PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }
}
