package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDate;

/** Foreign keys are plain ids: observeeId -> observee, sectionId -> course_section. */
@Table("evaluation_signup")
public record EvaluationSignup(
        @Id Integer signupId,
        Integer observeeId,
        Integer sectionId,
        LocalDate signupDate,
        String status) {
}
