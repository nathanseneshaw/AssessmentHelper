package edu.utdallas.cs4485.assessmenthelper.entity;

import java.time.LocalDate;
import org.springframework.data.annotation.Id;

public record Semester(
        @Id Integer semesterId, String term, Integer academicYear, LocalDate startDate, LocalDate endDate) {}
