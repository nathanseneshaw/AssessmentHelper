package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalTime;

/** Foreign keys are plain ids: courseId -> course, semesterId -> semester. */
@Table("course_section")
public record CourseSection(
        @Id Integer sectionId,
        Integer courseId,
        Integer semesterId,
        String sectionNumber,
        String meetingDays,
        LocalTime startTime,
        LocalTime endTime,
        String location) {
}
