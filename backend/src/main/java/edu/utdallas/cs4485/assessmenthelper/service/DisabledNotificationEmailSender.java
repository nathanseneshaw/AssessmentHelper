package edu.utdallas.cs4485.assessmenthelper.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Keeps the existing simulated-notification behavior until SMTP is enabled. */
@Component
@ConditionalOnProperty(prefix = "app.email", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledNotificationEmailSender implements NotificationEmailSender {
    @Override
    public void send(String recipient, String subject, String body) {
        // Intentionally disabled; notification history is still saved by the service.
    }
}
