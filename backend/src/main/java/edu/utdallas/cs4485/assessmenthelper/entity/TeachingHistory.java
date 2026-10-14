package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** Foreign keys are plain ids: professorId -> professor, sectionId -> course_section. */
@Table("teaching_history")
public record TeachingHistory(
        @Id Integer teachingHistoryId,
        Integer professorId,
        Integer sectionId) {
}
