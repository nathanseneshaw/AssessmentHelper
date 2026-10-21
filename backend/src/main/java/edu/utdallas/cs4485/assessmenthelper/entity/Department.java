package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;

/** Foreign keys are plain ids: schoolId -> school. */
public record Department(@Id Integer departmentId, Integer schoolId, String departmentName, String departmentCode) {}
