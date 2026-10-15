package edu.utdallas.cs4485.assessmenthelper.entity;

import java.time.LocalTime;
import org.springframework.data.annotation.Id;

/** Foreign keys are plain ids: courseId -> course, semesterId -> semester. */
public record CourseSection(
        @Id Integer sectionId,
        Integer courseId,
        Integer semesterId,
        String sectionNumber,
        String meetingDays,
        LocalTime startTime,
        LocalTime endTime,
        String location) {}
