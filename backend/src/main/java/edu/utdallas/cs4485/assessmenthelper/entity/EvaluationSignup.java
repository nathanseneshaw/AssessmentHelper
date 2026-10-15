package edu.utdallas.cs4485.assessmenthelper.entity;

import java.time.LocalDate;
import org.springframework.data.annotation.Id;

/** Foreign keys are plain ids: observeeId -> observee, sectionId -> course_section. */
public record EvaluationSignup(
        @Id Integer signupId, Integer observeeId, Integer sectionId, LocalDate signupDate, String status) {}
