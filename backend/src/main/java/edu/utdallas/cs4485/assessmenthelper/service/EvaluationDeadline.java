package edu.utdallas.cs4485.assessmenthelper.service;

import java.time.LocalDate;

/**
 * When a professor is next due to be evaluated.
 *
 * @param lastEvaluationDate the date of the latest completed observation, or null if never evaluated
 * @param nextDueDate last evaluation (or hire date if never evaluated) plus the rank's interval, or null if neither
 *     date is known
 * @param overdue true if the due date is before today
 */
public record EvaluationDeadline(
        Integer professorId,
        String professorRank,
        Integer evaluationIntervalYears,
        LocalDate lastEvaluationDate,
        LocalDate nextDueDate,
        boolean overdue) {}
