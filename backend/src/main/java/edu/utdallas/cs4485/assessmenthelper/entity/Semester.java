package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDate;

@Table("semester")
public record Semester(
        @Id Integer semesterId,
        String term,
        Integer academicYear,
        LocalDate startDate,
        LocalDate endDate) {
}
