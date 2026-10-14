package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** Foreign keys are plain ids: professorId -> professor, cycleId -> evaluation_cycle. */
@Table("observee")
public record Observee(
        @Id Integer observeeId,
        Integer professorId,
        Integer cycleId,
        String status) {
}
