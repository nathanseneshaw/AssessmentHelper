package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;

/** Foreign keys are plain ids: departmentId -> department. */
public record Course(
        @Id Integer courseId,
        Integer departmentId,
        String courseCode,
        String courseNumber,
        String courseName,
        String courseLevel) {}
