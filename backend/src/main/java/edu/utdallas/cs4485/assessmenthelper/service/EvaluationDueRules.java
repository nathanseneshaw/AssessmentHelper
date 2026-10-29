package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationPolicy;
import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.entity.Semester;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The single set of rules for when a professor is due or overdue for evaluation. The AC dashboard
 * ({@link EvaluationEligibilityService}) and the deadline endpoint ({@link EvaluationDeadlineService}) both call
 * this class, so they cannot disagree.
 *
 * <ol>
 *   <li>Only a cycle whose observee status is {@code COMPLETED}, with an observation completed on or before today,
 *       counts as an evaluation ({@link #countsAsCompleted}). The latest one is the anchor; with none, the hire date
 *       is the anchor.
 *   <li>The first due date is the anchor plus the rank's interval. Later due dates repeat every interval.
 *   <li>Everything is judged against the current semester, not against today. A professor is <b>due</b> if a due
 *       date falls on or before the semester's end and no earlier than its start. A professor is <b>overdue</b> only
 *       if the first due date is before the semester started and no recurrence lands in the current semester.
 *       Otherwise the professor is upcoming.
 *   <li>Inactive professors and professors whose policy is inactive are neither due nor overdue
 *       ({@link #applies}).
 * </ol>
 */
final class EvaluationDueRules {

    private EvaluationDueRules() {}

    /**
     * @param firstDue anchor plus one interval
     * @param nextDue the date to show: the recurrence inside the current semester if due, otherwise the first due date
     * @param dueThisCycle a due date falls inside the current semester (or earlier recurrences were skipped into it)
     * @param overdue the obligation was missed in an earlier semester and nothing is due this semester
     */
    record Result(LocalDate firstDue, LocalDate nextDue, boolean dueThisCycle, boolean overdue) {}

    /** Inactive professors and inactive policies are excluded from due and overdue. */
    static boolean applies(Professor professor, EvaluationPolicy policy) {
        return Boolean.TRUE.equals(professor.activeStatus()) && Boolean.TRUE.equals(policy.activeStatus());
    }

    /** An evaluation counts only if the observee status is COMPLETED and it was completed by today. */
    static boolean countsAsCompleted(String observeeStatus, LocalDateTime completedAt, LocalDate today) {
        return "COMPLETED".equalsIgnoreCase(observeeStatus)
                && completedAt != null
                && !completedAt.toLocalDate().isAfter(today);
    }

    static Result calculate(LocalDate anchor, int intervalYears, Semester semester) {
        if (intervalYears < 1) throw new IllegalArgumentException("Evaluation intervals must be positive.");
        LocalDate firstDue = anchor.plusYears(intervalYears);
        LocalDate recurrence = firstDue;
        int years = intervalYears;
        while (recurrence.isBefore(semester.startDate())) {
            years += intervalYears;
            recurrence = anchor.plusYears(years);
        }
        boolean dueThisCycle = !recurrence.isAfter(semester.endDate());
        boolean overdue = firstDue.isBefore(semester.startDate()) && !dueThisCycle;
        return new Result(firstDue, dueThisCycle ? recurrence : firstDue, dueThisCycle, overdue);
    }
}
