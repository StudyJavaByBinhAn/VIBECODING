package com.vibecode.emailservice.listener;

import com.vibecode.emailservice.entity.SentEmail;
import com.vibecode.emailservice.enums.EmailStatus;
import com.vibecode.emailservice.event.AppointmentBookedPayload;
import com.vibecode.emailservice.event.AppointmentCancelledPayload;
import com.vibecode.emailservice.event.KafkaTopics;
import com.vibecode.emailservice.event.PasswordResetRequestedPayload;
import com.vibecode.emailservice.repository.SentEmailRepository;
import com.vibecode.emailservice.service.EmailSenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;

// Mỗi listener tự dựng template tiếng Việt (port từ AppointmentService/AuthService bên
// booking-service, đúng quyết định kiến trúc: email-service sở hữu template của nó, payload
// Kafka chỉ chứa field có cấu trúc chứ không phải text dựng sẵn).
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailEventListener {

    private final ObjectMapper objectMapper;
    private final EmailSenderService emailSenderService;
    private final SentEmailRepository sentEmailRepository;
    private final Clock clock;

    @KafkaListener(topics = KafkaTopics.APPOINTMENT_BOOKED)
    public void onAppointmentBooked(String message) {
        AppointmentBookedPayload payload = parsePayload(message, AppointmentBookedPayload.class);
        String subject = "Xác nhận đặt lịch hẹn";
        String body = "Bạn đã đặt lịch hẹn thành công với " + payload.getDentistName()
                + " (" + payload.getServiceName() + ") vào lúc " + payload.getStartTime()
                + " ngày " + payload.getAppointmentDate() + ".";
        sendAndRecord(payload.getPatientEmail(), subject, body, KafkaTopics.APPOINTMENT_BOOKED);
    }

    @KafkaListener(topics = KafkaTopics.APPOINTMENT_CANCELLED)
    public void onAppointmentCancelled(String message) {
        AppointmentCancelledPayload payload = parsePayload(message, AppointmentCancelledPayload.class);
        String subject = "Huỷ lịch hẹn";
        String body = "Lịch hẹn với " + payload.getDentistName() + " vào lúc " + payload.getStartTime()
                + " ngày " + payload.getAppointmentDate() + " đã được huỷ.";
        sendAndRecord(payload.getPatientEmail(), subject, body, KafkaTopics.APPOINTMENT_CANCELLED);
    }

    @KafkaListener(topics = KafkaTopics.AUTH_PASSWORD_RESET_REQUESTED)
    public void onPasswordResetRequested(String message) {
        PasswordResetRequestedPayload payload = parsePayload(message, PasswordResetRequestedPayload.class);
        String subject = "Đặt lại mật khẩu";
        String body = "Mã đặt lại mật khẩu của bạn là: " + payload.getResetToken() + "\n"
                + "Mã có hiệu lực đến " + payload.getExpiresAt() + ". "
                + "Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này.";
        sendAndRecord(payload.getUserEmail(), subject, body, KafkaTopics.AUTH_PASSWORD_RESET_REQUESTED);
    }

    // DomainEvent<T> là generic bên producer nên không deserialize thẳng bằng type token được —
    // đọc message thô thành cây JSON rồi convert đúng field "data" sang payload type cụ thể.
    private <T> T parsePayload(String message, Class<T> type) {
        JsonNode root = objectMapper.readTree(message);
        return objectMapper.treeToValue(root.get("data"), type);
    }

    private void sendAndRecord(String to, String subject, String body, String eventType) {
        boolean sent = emailSenderService.send(to, subject, body);
        sentEmailRepository.save(SentEmail.builder()
                .recipient(to)
                .subject(subject)
                .eventType(eventType)
                .status(sent ? EmailStatus.SENT : EmailStatus.FAILED)
                .sentAt(Instant.now(clock))
                .build());
        log.info("Xử lý event {} cho {} — status={}", eventType, to, sent ? "SENT" : "FAILED");
    }
}
