package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** Foreign keys are plain ids: observationId -> observation, criterionId -> observation_criterion. */
@Table("observation_response")
public record ObservationResponse(
        @Id Integer responseId, Integer observationId, Integer criterionId, Integer rating, String comments) {}
