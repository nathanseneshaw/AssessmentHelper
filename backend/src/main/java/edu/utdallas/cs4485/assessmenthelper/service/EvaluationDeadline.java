package edu.utdallas.cs4485.assessmenthelper.service;

import java.time.LocalDate;

/**
 * When a professor is next due to be evaluated.
 *
 * @param lastEvaluationDate the date of the latest evaluation whose observee status is COMPLETED, or null if never evaluated
 * @param nextDueDate last evaluation (or hire date if never evaluated) plus the rank's interval, or null if neither
 *     date is known
 * @param overdue true if the obligation was missed in an earlier semester and nothing is due in the current one;
 *     false for inactive professors or policies. Same rules as the AC dashboard.
 */
public record EvaluationDeadline(
        Integer professorId,
        String professorRank,
        Integer evaluationIntervalYears,
        LocalDate lastEvaluationDate,
        LocalDate nextDueDate,
        boolean overdue) {}
