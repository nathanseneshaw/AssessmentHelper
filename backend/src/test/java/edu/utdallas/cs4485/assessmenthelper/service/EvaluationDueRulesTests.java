package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationPolicy;
import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.entity.Semester;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** The semester matches data.sql's Fall 2026. */
class EvaluationDueRulesTests {
    private static final Semester FALL_2026 =
            new Semester(13, "Fall", 2026, LocalDate.of(2026, 8, 24), LocalDate.of(2026, 12, 16));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

    @Test
    void aDueDateInsideTheSemesterIsDue() {
        assertThat(calculate("2025-09-01", 1)).isEqualTo(result("2026-09-01", "2026-09-01", true, false));
    }

    @Test
    void aDueDateBeforeTheSemesterIsOverdue() {
        // The next recurrence, 2027-08-23, is after the semester, so the missed date is the one to show.
        assertThat(calculate("2025-08-23", 1)).isEqualTo(result("2026-08-23", "2026-08-23", false, true));
    }

    @Test
    void aDueDateAfterTheSemesterIsUpcoming() {
        assertThat(calculate("2025-09-01", 2)).isEqualTo(result("2027-09-01", "2027-09-01", false, false));
    }

    @Test
    void aMissedDateWhoseRecurrenceFallsInTheSemesterIsDueNotOverdue() {
        // Due 2025-09-01 and missed, but the yearly recurrence lands in Fall 2026, so that is the date to show.
        assertThat(calculate("2024-09-01", 1)).isEqualTo(result("2025-09-01", "2026-09-01", true, false));
    }

    @Test
    void theSemestersFirstAndLastDaysAreInside() {
        assertThat(calculate("2025-08-24", 1).dueThisCycle()).isTrue();
        assertThat(calculate("2025-12-16", 1).dueThisCycle()).isTrue();
        assertThat(calculate("2025-12-17", 1)).isEqualTo(result("2026-12-17", "2026-12-17", false, false));
    }

    @Test
    void intervalsMustBePositive() {
        assertThatThrownBy(() -> calculate("2025-09-01", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyActiveProfessorsWithAnActivePolicyApply() {
        var policy = policy(true);
        assertThat(EvaluationDueRules.applies(professor(true), policy)).isTrue();
        assertThat(EvaluationDueRules.applies(professor(false), policy)).isFalse();
        assertThat(EvaluationDueRules.applies(professor(null), policy)).isFalse();
        assertThat(EvaluationDueRules.applies(professor(true), policy(false))).isFalse();
    }

    @Test
    void onlyACompletedCycleFinishedByTodayCounts() {
        var morning = TODAY.atTime(9, 0);
        assertThat(EvaluationDueRules.countsAsCompleted("COMPLETED", morning, TODAY))
                .isTrue();
        assertThat(EvaluationDueRules.countsAsCompleted("completed", morning, TODAY))
                .isTrue();
        assertThat(EvaluationDueRules.countsAsCompleted("ACTIVE", morning, TODAY))
                .isFalse();
        assertThat(EvaluationDueRules.countsAsCompleted("COMPLETED", null, TODAY))
                .isFalse();
        assertThat(EvaluationDueRules.countsAsCompleted("COMPLETED", morning.plusDays(1), TODAY))
                .isFalse();
    }

    private static EvaluationDueRules.Result calculate(String anchor, int intervalYears) {
        return EvaluationDueRules.calculate(LocalDate.parse(anchor), intervalYears, FALL_2026);
    }

    private static EvaluationDueRules.Result result(
            String firstDue, String nextDue, boolean dueThisCycle, boolean overdue) {
        return new EvaluationDueRules.Result(
                LocalDate.parse(firstDue), LocalDate.parse(nextDue), dueThisCycle, overdue);
    }

    private static Professor professor(Boolean active) {
        return new Professor(1, 1, "ASSOCIATE", "Test", "Professor", "test@example.edu", null, active);
    }

    private static EvaluationPolicy policy(boolean active) {
        return new EvaluationPolicy("ASSOCIATE", 3, active, null, null, 1L);
    }
}
