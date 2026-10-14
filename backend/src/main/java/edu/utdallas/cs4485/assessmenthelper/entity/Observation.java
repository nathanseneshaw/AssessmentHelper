package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Foreign keys are plain ids: signupId -> evaluation_signup, observerId -> observer, templateId -> observation_template. */
@Table("observation")
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
        LocalDateTime completedAt) {
}
