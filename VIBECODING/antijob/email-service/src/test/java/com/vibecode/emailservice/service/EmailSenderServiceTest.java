package com.vibecode.emailservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailSenderServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailSenderService emailSenderService;

    @BeforeEach
    void setUp() {
        emailSenderService = new EmailSenderService(mailSender);
        ReflectionTestUtils.setField(emailSenderService, "from", "no-reply@vibecode-dental.local");
    }

    @Test
    void send_buildsMessageWithFromToSubjectBody_returnsTrue() {
        boolean result = emailSenderService.send("patient@example.com", "Xác nhận đặt lịch hẹn", "Nội dung email");

        assertThat(result).isTrue();
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getFrom()).isEqualTo("no-reply@vibecode-dental.local");
        assertThat(sent.getTo()).containsExactly("patient@example.com");
        assertThat(sent.getSubject()).isEqualTo("Xác nhận đặt lịch hẹn");
        assertThat(sent.getText()).isEqualTo("Nội dung email");
    }

    @Test
    void send_mailSenderThrows_doesNotPropagate_returnsFalse() {
        doThrow(new MailSendException("SMTP down"))
                .when(mailSender).send(ArgumentMatchers.any(SimpleMailMessage.class));

        boolean result = emailSenderService.send("patient@example.com", "subject", "body");

        assertThat(result).isFalse();
    }
}
