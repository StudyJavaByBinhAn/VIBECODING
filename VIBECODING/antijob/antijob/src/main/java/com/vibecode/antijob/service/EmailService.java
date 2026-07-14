package com.vibecode.antijob.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${mail.from}")
    private String from;

    // Gửi mail là side-effect, không phải business rule — lỗi SMTP (MailHog/relay down...)
    // không được làm fail transaction chính (đặt lịch, huỷ lịch, quên mật khẩu), chỉ log lại.
    public void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Gửi email thất bại tới {} (subject: {})", to, subject, e);
        }
    }
}
