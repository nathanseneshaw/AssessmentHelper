package edu.utdallas.cs4485.assessmenthelper.service;

import java.time.LocalDate;

/**
 * One section a professor has taught.
 *
 * @param level the 1000, 2000, 3000, ... level that observer eligibility compares, taken from the course number. Null
 *     when the course number is not four digits (for example {@code 4V95}). This is not the course's
 *     {@code courseLevel} column, which holds UNDERGRADUATE or GRADUATE.
 */
public record TeachingHistoryEntry(
        Integer sectionId,
        Integer courseId,
        String courseCode,
        String courseNumber,
        String courseName,
        Integer level,
        String sectionNumber,
        String term,
        Integer academicYear,
        LocalDate semesterStartDate,
        LocalDate semesterEndDate) {}
