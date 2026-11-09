package edu.utdallas.cs4485.assessmenthelper.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.email", name = "enabled", havingValue = "true")
public class SmtpNotificationEmailSender implements NotificationEmailSender {
    private final JavaMailSender mailSender;
    private final String from;
    private final boolean testMode;
    private final String testRecipient;

    public SmtpNotificationEmailSender(
            JavaMailSender mailSender,
            @Value("${app.email.from:}") String from,
            @Value("${app.email.test-mode:false}") boolean testMode,
            @Value("${app.email.test-recipient:}") String testRecipient) {
        this.mailSender = mailSender;
        this.from = from;
        this.testMode = testMode;
        this.testRecipient = testRecipient;
    }

    @Override
    public void send(String recipient, String subject, String body) {
        if (from.isBlank()) {
            throw new IllegalStateException("Set SMTP_FROM or SMTP_USERNAME before enabling email delivery.");
        }
        if (testMode && testRecipient.isBlank()) {
            throw new IllegalStateException("Set APP_EMAIL_TEST_RECIPIENT when APP_EMAIL_TEST_MODE is enabled.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(testMode ? testRecipient : recipient);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
