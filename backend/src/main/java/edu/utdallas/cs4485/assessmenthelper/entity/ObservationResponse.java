package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;

/** Foreign keys are plain ids: observationId -> observation, criterionId -> observation_criterion. */
public record ObservationResponse(
        @Id Integer responseId, Integer observationId, Integer criterionId, Integer rating, String comments) {}
