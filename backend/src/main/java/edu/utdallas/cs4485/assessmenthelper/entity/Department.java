package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;

public record Department(@Id Integer departmentId, String departmentName, String departmentCode) {}
