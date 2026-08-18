package com.vibecode.customercare.dto;

import lombok.Data;

@Data
public class ChatSendRequest {

    // Chỉ bắt buộc khi người gửi là staff (ADMIN/RECEPTIONIST/DENTIST) — patient luôn gửi vào
    // đúng 1 conversation của mình, server tự find-or-create theo email, không cần client biết id.
    private String conversationId;

    // Validate thủ công ở ChatService.sendMessage() (không @Valid vì @MessageMapping của STOMP
    // không tự chạy Bean Validation như @RequestBody REST).
    private String content;
}
