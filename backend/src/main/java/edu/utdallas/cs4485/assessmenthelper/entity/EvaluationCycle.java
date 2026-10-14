package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDate;

/** Foreign keys are plain ids: semesterId -> semester. */
@Table("evaluation_cycle")
public record EvaluationCycle(
        @Id Integer cycleId,
        Integer semesterId,
        LocalDate signupDeadline,
        LocalDate observationDeadline,
        LocalDate feedbackDeadline,
        String status) {
}
