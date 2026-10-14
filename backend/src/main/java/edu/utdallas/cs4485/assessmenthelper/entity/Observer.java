package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** Foreign keys are plain ids: professorId -> professor, cycleId -> evaluation_cycle. */
@Table("observer")
public record Observer(@Id Integer observerId, Integer professorId, Integer cycleId, Boolean activeStatus) {}
