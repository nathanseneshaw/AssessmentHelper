package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationCycleRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.SemesterRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Saves a completed cycle's committee data so later profile changes do not rewrite its report. */
@Service
public class CycleDashboardSnapshotService {
    private final EvaluationCycleRepository cycles;
    private final SemesterRepository semesters;
    private final EvaluationEligibilityService eligibility;
    private final ObserverEligibilityService matching;
    private final AcDashboardService dashboard;
    private final MissingObservationService missing;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final Clock clock;

    public CycleDashboardSnapshotService(
            EvaluationCycleRepository cycles,
            SemesterRepository semesters,
            EvaluationEligibilityService eligibility,
            ObserverEligibilityService matching,
            AcDashboardService dashboard,
            MissingObservationService missing,
            JdbcTemplate jdbc,
            JsonMapper json,
            Clock clock) {
        this.cycles = cycles;
        this.semesters = semesters;
        this.eligibility = eligibility;
        this.matching = matching;
        this.dashboard = dashboard;
        this.missing = missing;
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    public record Snapshot(
            EvaluationEligibilityService.Queue all,
            EvaluationEligibilityService.Queue due,
            EvaluationEligibilityService.Queue overdue,
            ObserverEligibilityService.AcState matching,
            AcDashboardService.Metrics metrics,
            AcDashboardService.Observations observations,
            MissingObservationService.MissingSignups missing) {}

    @Transactional
    public Snapshot get(int cycleId) {
        var saved = jdbc.query("""
                SELECT snap.snapshot_json FROM cycle_dashboard_snapshot snap
                JOIN evaluation_cycle c ON c.cycle_id = snap.cycle_id
                JOIN semester s ON s.semester_id = c.semester_id
                WHERE snap.cycle_id = ? AND s.end_date < ?
                """, (row, index) -> row.getString(1), cycleId, LocalDate.now(clock));
        if (!saved.isEmpty()) return json.readValue(saved.getFirst(), Snapshot.class);
        var cycle = cycles.findForUpdateByCycleId(cycleId)
                .orElseThrow(() -> new NotFoundException("Evaluation cycle not found."));
        var semester =
                semesters.findById(cycle.semesterId()).orElseThrow(() -> new NotFoundException("Semester not found."));
        boolean past = semester.endDate() != null && LocalDate.now(clock).isAfter(semester.endDate());
        if (past) {
            saved = jdbc.query(
                    "SELECT snapshot_json FROM cycle_dashboard_snapshot WHERE cycle_id = ?",
                    (row, index) -> row.getString(1),
                    cycleId);
            if (!saved.isEmpty()) return json.readValue(saved.getFirst(), Snapshot.class);
        }
        var snapshot = new Snapshot(
                eligibility.findAll(cycleId),
                eligibility.findDue(cycleId),
                eligibility.findOverdue(cycleId),
                matching.acState(cycleId),
                dashboard.metrics(cycleId),
                dashboard.observations(cycleId),
                missing.findMissingSignups(cycleId));
        if (past)
            jdbc.update(
                    "INSERT INTO cycle_dashboard_snapshot (cycle_id, snapshot_json, captured_at) VALUES (?, ?, ?)",
                    cycleId,
                    json.writeValueAsString(snapshot),
                    LocalDateTime.now(clock));
        return snapshot;
    }
}
