package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;

/** Foreign keys are plain ids: professorId -> professor, cycleId -> evaluation_cycle. */
public record Observer(@Id Integer observerId, Integer professorId, Integer cycleId, Boolean activeStatus) {}
