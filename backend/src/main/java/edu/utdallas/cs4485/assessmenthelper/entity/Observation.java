package edu.utdallas.cs4485.assessmenthelper.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.springframework.data.annotation.Id;

/** Foreign keys are plain ids: signupId -> evaluation_signup, observerId -> observer, templateId -> observation_template. */
public record Observation(
        @Id Integer observationId,
        Integer signupId,
        Integer observerId,
        Integer templateId,
        LocalDate observationDate,
        LocalTime startTime,
        String status,
        Boolean observeeSigned,
        Boolean observerSigned,
        LocalDateTime observeeSignedAt,
        LocalDateTime observerSignedAt,
        LocalDateTime completedAt) {}
