package com.vibecode.customercare.controller;

import com.vibecode.customercare.dto.ChatMessageResponse;
import com.vibecode.customercare.dto.ConversationResponse;
import com.vibecode.customercare.dto.PageResponse;
import com.vibecode.customercare.exception.ApiResponse;
import com.vibecode.customercare.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ChatService chatService;

    // Staff (ADMIN/RECEPTIONIST/DENTIST): trả về tất cả conversation, mới nhất trước.
    // Patient: trả về đúng conversation của chính mình (rỗng nếu chưa từng nhắn tin nào).
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ConversationResponse>>> list(
            Authentication authentication,
            @PageableDefault(size = 20, sort = "lastMessageAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(chatService.listConversations(authentication, pageable)));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<ApiResponse<PageResponse<ChatMessageResponse>>> messages(
            @PathVariable String id,
            Authentication authentication,
            @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(chatService.getMessages(id, authentication, pageable)));
    }
}
