package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("department")
public record Department(@Id Integer departmentId, String departmentName, String departmentCode) {}
