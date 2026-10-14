package edu.utdallas.cs4485.assessmenthelper.entity;

import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** Foreign keys are plain ids: observeeId -> observee, sectionId -> course_section. */
@Table("evaluation_signup")
public record EvaluationSignup(
        @Id Integer signupId, Integer observeeId, Integer sectionId, LocalDate signupDate, String status) {}
