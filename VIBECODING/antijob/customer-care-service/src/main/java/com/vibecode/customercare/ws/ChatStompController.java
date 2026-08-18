package com.vibecode.customercare.ws;

import com.vibecode.customercare.dto.ChatSendRequest;
import com.vibecode.customercare.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final ChatService chatService;

    // Đích duy nhất cho cả patient lẫn staff — không nhét conversationId vào destination vì
    // patient chưa có id nào cho tới khi gửi tin đầu tiên (server tự find-or-create).
    @MessageMapping("/chat.send")
    public void send(ChatSendRequest req, Principal principal) {
        UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken) principal;
        String email = auth.getName();
        String role = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .map(a -> a.replace("ROLE_", ""))
                .orElseThrow();
        chatService.sendMessage(email, role, req);
    }
}
