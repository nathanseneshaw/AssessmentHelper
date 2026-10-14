package edu.utdallas.cs4485.assessmenthelper.entity;

import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** Foreign keys are plain ids: cycleId -> evaluation_cycle, professorId -> professor, signupId -> evaluation_signup. */
@Table("process_feedback")
public record ProcessFeedback(
        @Id Integer feedbackId,
        Integer cycleId,
        Integer professorId,
        Integer signupId,
        Integer overallRating,
        String difficulties,
        String improvementComments,
        String observeeComment,
        LocalDate submittedDate) {}
