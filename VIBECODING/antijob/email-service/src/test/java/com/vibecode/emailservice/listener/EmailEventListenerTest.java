package com.vibecode.emailservice.listener;

import com.vibecode.emailservice.entity.SentEmail;
import com.vibecode.emailservice.enums.EmailStatus;
import com.vibecode.emailservice.repository.SentEmailRepository;
import com.vibecode.emailservice.service.EmailSenderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailEventListenerTest {

    @Mock
    private EmailSenderService emailSenderService;
    @Mock
    private SentEmailRepository sentEmailRepository;

    private EmailEventListener listener;

    private static final Instant FIXED_NOW = Instant.parse("2026-08-10T09:00:00Z");

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        listener = new EmailEventListener(new ObjectMapper(), emailSenderService, sentEmailRepository, clock);
    }

    @Test
    void onAppointmentBooked_parsesPayload_sendsEmail_recordsSent() {
        when(emailSenderService.send(eq("patient@example.com"), any(), any())).thenReturn(true);
        String message = """
                {"eventId":"e1","eventType":"appointment.booked","version":1,"occurredAt":"2026-08-10T08:00:00Z",
                 "data":{"appointmentId":42,"patientEmail":"patient@example.com","patientName":"Nguyen Van A",
                 "dentistName":"BS. Tran","serviceName":"Trám răng","appointmentDate":"2026-08-15","startTime":"09:00:00"}}
                """;

        listener.onAppointmentBooked(message);

        verify(emailSenderService).send(eq("patient@example.com"), eq("Xác nhận đặt lịch hẹn"), any());
        ArgumentCaptor<SentEmail> captor = ArgumentCaptor.forClass(SentEmail.class);
        verify(sentEmailRepository).save(captor.capture());
        SentEmail saved = captor.getValue();
        assertThat(saved.getRecipient()).isEqualTo("patient@example.com");
        assertThat(saved.getEventType()).isEqualTo("appointment.booked");
        assertThat(saved.getStatus()).isEqualTo(EmailStatus.SENT);
        assertThat(saved.getSentAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void onAppointmentCancelled_sendFails_recordsFailed() {
        when(emailSenderService.send(eq("patient@example.com"), any(), any())).thenReturn(false);
        String message = """
                {"eventId":"e2","eventType":"appointment.cancelled","version":1,"occurredAt":"2026-08-10T08:00:00Z",
                 "data":{"appointmentId":42,"patientEmail":"patient@example.com","dentistName":"BS. Tran",
                 "appointmentDate":"2026-08-15","startTime":"09:00:00"}}
                """;

        listener.onAppointmentCancelled(message);

        ArgumentCaptor<SentEmail> captor = ArgumentCaptor.forClass(SentEmail.class);
        verify(sentEmailRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EmailStatus.FAILED);
    }

    @Test
    void onPasswordResetRequested_parsesPayload_sendsResetEmail() {
        when(emailSenderService.send(eq("user@example.com"), any(), any())).thenReturn(true);
        String message = """
                {"eventId":"e3","eventType":"auth.password-reset-requested","version":1,"occurredAt":"2026-08-10T08:00:00Z",
                 "data":{"userEmail":"user@example.com","resetToken":"abc-123","expiresAt":"2026-08-10T09:30:00"}}
                """;

        listener.onPasswordResetRequested(message);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSenderService).send(eq("user@example.com"), eq("Đặt lại mật khẩu"), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains("abc-123");
    }
}
