package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** Foreign keys are plain ids: templateId -> observation_template. */
@Table("observation_criterion")
public record ObservationCriterion(
        @Id Integer criterionId,
        Integer templateId,
        String criterionText,
        String questionType,
        Boolean required,
        Integer displayOrder,
        Integer ratingMin,
        Integer ratingMax) {
}
