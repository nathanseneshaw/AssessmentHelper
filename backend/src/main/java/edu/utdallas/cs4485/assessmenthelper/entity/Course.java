package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** Foreign keys are plain ids: departmentId -> department. */
@Table("course")
public record Course(
        @Id Integer courseId,
        Integer departmentId,
        String courseCode,
        String courseNumber,
        String courseName,
        String courseLevel) {
}
