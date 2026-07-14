package com.vibecode.antijob.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "from", "no-reply@vibecode-dental.local");
    }

    @Test
    void send_buildsMessageWithFromToSubjectBody() {
        emailService.send("patient@example.com", "Xác nhận đặt lịch hẹn", "Nội dung email");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getFrom()).isEqualTo("no-reply@vibecode-dental.local");
        assertThat(sent.getTo()).containsExactly("patient@example.com");
        assertThat(sent.getSubject()).isEqualTo("Xác nhận đặt lịch hẹn");
        assertThat(sent.getText()).isEqualTo("Nội dung email");
    }

    @Test
    void send_mailSenderThrows_doesNotPropagateException() {
        doThrow(new org.springframework.mail.MailSendException("SMTP down"))
                .when(mailSender).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));

        assertThatCode(() -> emailService.send("patient@example.com", "subject", "body"))
                .doesNotThrowAnyException();
    }
}
