package com.vibecode.customercare.config;

import com.vibecode.customercare.security.JwtAuthenticationResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

// Auth qua CONNECT header (Authorization: Bearer ...), không phải query param — tránh lộ token
// vào access log/browser history (quyết định đã chốt trong plan file). Chỉ command CONNECT cần
// verify; các frame sau (SEND/SUBSCRIBE...) tái dùng Principal đã gắn vào session ở bước CONNECT.
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtAuthenticationResolver jwtAuthenticationResolver;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String header = accessor.getFirstNativeHeader("Authorization");
            String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
            Authentication authentication = jwtAuthenticationResolver.resolve(token);
            if (authentication == null) {
                throw new AccessDeniedException("JWT không hợp lệ hoặc đã bị thu hồi");
            }
            accessor.setUser(authentication);
        }
        return message;
    }
}
