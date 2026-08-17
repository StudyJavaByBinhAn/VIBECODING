package com.vibecode.emailservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSenderService {

    private final JavaMailSender mailSender;

    @Value("${mail.from}")
    private String from;

    // Trả về false thay vì nuốt hẳn exception (khác EmailService bên booking-service) vì ở đây
    // gửi mail LÀ nghiệp vụ chính của service này — caller (EmailEventListener) cần biết kết quả
    // để ghi đúng status vào audit log sent_emails, không phải chỉ log rồi bỏ qua.
    public boolean send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            log.error("Gửi email thất bại tới {} (subject: {})", to, subject, e);
            return false;
        }
    }
}
