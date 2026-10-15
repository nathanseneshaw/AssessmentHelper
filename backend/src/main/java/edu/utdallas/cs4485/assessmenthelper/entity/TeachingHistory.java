package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;

/** Foreign keys are plain ids: professorId -> professor, sectionId -> course_section. */
public record TeachingHistory(@Id Integer teachingHistoryId, Integer professorId, Integer sectionId) {}
