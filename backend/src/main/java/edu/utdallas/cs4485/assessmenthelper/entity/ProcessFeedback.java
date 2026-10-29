package edu.utdallas.cs4485.assessmenthelper.entity;

import java.time.LocalDate;
import org.springframework.data.annotation.Id;

/** Foreign keys are plain ids: cycleId -> evaluation_cycle, professorId -> professor, signupId -> evaluation_signup. */
public record ProcessFeedback(
        @Id Integer feedbackId,
        Integer cycleId,
        Integer professorId,
        Integer signupId,
        Integer overallRating,
        String difficulties,
        String improvementComments,
        String observeeComment,
        LocalDate submittedDate,
        Integer clarityRating,
        Integer schedulingRating,
        Integer communicationRating,
        Integer organizationRating,
        Integer usefulnessRating) {}
