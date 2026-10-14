package edu.utdallas.cs4485.assessmenthelper.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** Foreign keys are plain ids: professorId -> professor, cycleId -> evaluation_cycle. */
@Table("evaluation_eligibility")
public record EvaluationEligibility(
        @Id Integer eligibilityId,
        Integer professorId,
        Integer cycleId,
        LocalDate lastEvaluationDate,
        LocalDate nextDueDate,
        String eligibilityStatus,
        LocalDateTime determinedAt) {}
