package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;

class SmtpNotificationEmailSenderTests {
    private final JavaMailSender mail = mock(JavaMailSender.class);

    @Test
    void smtpFailureReturnsServiceUnavailableWithoutExposingServerDetails() {
        doThrow(new MailSendException("private SMTP details")).when(mail).send(any(SimpleMailMessage.class));
        var sender = new SmtpNotificationEmailSender(mail, "sender@test.edu", false, "");
        assertThatThrownBy(() -> sender.send("faculty@test.edu", "Subject", "Body"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getReason()).contains("Please try again later").doesNotContain("private SMTP details");
                });
    }

    @Test
    void missingConfigurationReturnsServiceUnavailableBeforeSending() {
        for (var sender : new SmtpNotificationEmailSender[] {
            new SmtpNotificationEmailSender(mail, "", false, ""),
            new SmtpNotificationEmailSender(mail, "sender@test.edu", true, "")
        }) {
            assertThatThrownBy(() -> sender.send("faculty@test.edu", "Subject", "Body"))
                    .isInstanceOfSatisfying(
                            ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        }
        verifyNoInteractions(mail);
    }

    @Test
    void testModeRedirectsMailToConfiguredRecipient() {
        new SmtpNotificationEmailSender(mail, "sender@test.edu", true, "test@test.edu")
                .send("faculty@test.edu", "Subject", "Body");
        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly("test@test.edu");
        assertThat(message.getValue().getFrom()).isEqualTo("sender@test.edu");
        assertThat(message.getValue().getSubject()).isEqualTo("Subject");
        assertThat(message.getValue().getText()).isEqualTo("Body");
    }
}
