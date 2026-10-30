package edu.utdallas.cs4485.assessmenthelper.entity;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;

public record ObservationAuditLog(
        @Id Integer logId,
        Integer observationId,
        String eventType,
        Integer actorProfessorId,
        Integer recipientProfessorId,
        LocalDateTime occurredAt) {}
