package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationCycleRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.SemesterRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Checks setup data and records the result once the semester begins. */
@Service
public class CycleReadinessService {
    private final EvaluationCycleRepository cycles;
    private final SemesterRepository semesters;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ObjectProvider<CycleDashboardSnapshotService> snapshots;

    public CycleReadinessService(
            EvaluationCycleRepository cycles,
            SemesterRepository semesters,
            JdbcTemplate jdbc,
            Clock clock,
            ObjectProvider<CycleDashboardSnapshotService> snapshots) {
        this.cycles = cycles;
        this.semesters = semesters;
        this.jdbc = jdbc;
        this.clock = clock;
        this.snapshots = snapshots;
    }

    public record Readiness(String state, String reason) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Readiness check(int cycleId) {
        if (!cycles.existsById(cycleId)) throw new NotFoundException("Evaluation cycle not found.");
        Integer managed =
                jdbc.queryForObject("SELECT COUNT(*) FROM cycle_readiness WHERE cycle_id = ?", Integer.class, cycleId);
        // Cycles that predate setup retain their existing behavior and do not need a write lock.
        if (managed == null || managed == 0) return new Readiness("READY", null);
        var cycle = cycles.findForUpdateByCycleId(cycleId)
                .orElseThrow(() -> new NotFoundException("Evaluation cycle not found."));
        var semester =
                semesters.findById(cycle.semesterId()).orElseThrow(() -> new NotFoundException("Semester not found."));
        var states = jdbc.query(
                "SELECT state, reason FROM cycle_readiness WHERE cycle_id = ? FOR UPDATE",
                (row, index) -> new Readiness(row.getString("state"), row.getString("reason")),
                cycleId);
        if (states.isEmpty()) throw new NotFoundException("Cycle readiness not found.");
        var stored = states.getFirst();
        if (!"PENDING".equals(stored.state())) return stored;

        String reason = null;
        if (semester.startDate() == null
                || semester.endDate() == null
                || cycle.signupDeadline() == null
                || cycle.observationDeadline() == null
                || cycle.feedbackDeadline() == null
                || cycle.signupDeadline().isBefore(semester.startDate())
                || !cycle.observationDeadline().isAfter(cycle.signupDeadline())
                || !cycle.feedbackDeadline().isAfter(cycle.observationDeadline())
                || cycle.feedbackDeadline().isAfter(semester.endDate())) {
            reason = "Semester dates and deadlines must be complete and in order.";
        } else {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM cycle_professor_roster WHERE cycle_id = ?", Integer.class, cycleId);
            Integer withoutSection = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM cycle_professor_roster r
                    WHERE r.cycle_id = ? AND NOT EXISTS (
                        SELECT 1 FROM teaching_history t JOIN course_section s ON s.section_id=t.section_id
                        WHERE t.professor_id=r.professor_id AND s.semester_id=?)
                    """, Integer.class, cycleId, semester.semesterId());
            if (count == null || count == 0) reason = "The professor roster is empty.";
            else if (withoutSection != null && withoutSection > 0)
                reason = "Some professors have no section assignment.";
        }
        if (semester.startDate() != null && !LocalDate.now(clock).isBefore(semester.startDate())) {
            String state = reason == null ? "READY" : "FAILED";
            if (reason == null) {
                preservePreviousCycles();
                activateRoster(cycleId);
            }
            jdbc.update("UPDATE cycle_readiness SET state = ?, reason = ? WHERE cycle_id = ?", state, reason, cycleId);
            return new Readiness(state, reason);
        }
        return new Readiness(reason == null ? "PENDING" : "NOT_READY", reason);
    }

    private void preservePreviousCycles() {
        var snapshotService = snapshots.getIfAvailable();
        if (snapshotService == null) return;
        var pending = jdbc.queryForList("""
                SELECT c.cycle_id FROM evaluation_cycle c JOIN semester s ON s.semester_id=c.semester_id
                WHERE s.end_date < ? AND NOT EXISTS
                    (SELECT 1 FROM cycle_dashboard_snapshot snap WHERE snap.cycle_id=c.cycle_id)
                AND ((s.term='Fall' AND s.academic_year=2026) OR EXISTS
                    (SELECT 1 FROM cycle_professor_roster r WHERE r.cycle_id=c.cycle_id))
                ORDER BY s.end_date
                """, Integer.class, LocalDate.now(clock));
        for (Integer previous : pending) snapshotService.get(previous);
    }

    private void activateRoster(int cycleId) {
        var roster = cycles.findRosterProfessors(cycleId);
        for (var professor : roster) {
            jdbc.update(
                    """
                    UPDATE professor SET department_id=?, professor_rank=?, first_name=?, last_name=?, hire_date=?, active_status=?
                    WHERE professor_id=?
                    """,
                    professor.departmentId(),
                    professor.professorRank(),
                    professor.firstName(),
                    professor.lastName(),
                    professor.hireDate(),
                    professor.activeStatus(),
                    professor.professorId());
        }
    }
}
