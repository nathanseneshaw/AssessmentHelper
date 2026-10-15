package edu.utdallas.cs4485.assessmenthelper.entity;

import java.time.LocalDate;
import org.springframework.data.annotation.Id;

/** Foreign keys are plain ids: semesterId -> semester. */
public record EvaluationCycle(
        @Id Integer cycleId,
        Integer semesterId,
        LocalDate signupDeadline,
        LocalDate observationDeadline,
        LocalDate feedbackDeadline,
        String status) {}
