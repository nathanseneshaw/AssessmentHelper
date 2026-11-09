package edu.utdallas.cs4485.assessmenthelper.service;

/** Sends an evaluation notification email when real delivery is configured. */
public interface NotificationEmailSender {
    void send(String recipient, String subject, String body);
}
