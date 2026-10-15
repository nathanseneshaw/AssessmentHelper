package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;

/** Foreign keys are plain ids: templateId -> observation_template. */
public record ObservationCriterion(
        @Id Integer criterionId,
        Integer templateId,
        String criterionText,
        String questionType,
        Boolean required,
        Integer displayOrder,
        Integer ratingMin,
        Integer ratingMax) {}
