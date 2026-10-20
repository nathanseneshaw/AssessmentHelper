package edu.utdallas.cs4485.assessmenthelper.entity;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;

/** A simulated email, retained as notification history. Never represents actual delivery. */
public record EvaluationNotification(
        @Id Integer notificationId,
        Integer professorId,
        Integer cycleId,
        String recipient,
        String subject,
        String body,
        LocalDateTime sentAt) {}
