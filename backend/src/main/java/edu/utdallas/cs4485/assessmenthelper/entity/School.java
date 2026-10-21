package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;

public record School(@Id Integer schoolId, String schoolName) {}
