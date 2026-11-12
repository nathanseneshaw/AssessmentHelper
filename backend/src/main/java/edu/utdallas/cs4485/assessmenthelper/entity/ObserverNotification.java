package edu.utdallas.cs4485.assessmenthelper.entity;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;

public record ObserverNotification(
        @Id Integer notificationId, Integer professorId, Integer signupId, String message, LocalDateTime createdAt) {}
