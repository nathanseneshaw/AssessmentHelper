package edu.utdallas.cs4485.assessmenthelper.service;

import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Finalizes pending cycles after their start date, including after an application restart. */
@Component
public class CycleReadinessScheduler {
    private static final Logger log = LoggerFactory.getLogger(CycleReadinessScheduler.class);
    private final JdbcTemplate jdbc;
    private final CycleReadinessService readiness;
    private final Clock clock;
    private final CycleDashboardSnapshotService snapshots;

    public CycleReadinessScheduler(
            JdbcTemplate jdbc, CycleReadinessService readiness, Clock clock, CycleDashboardSnapshotService snapshots) {
        this.jdbc = jdbc;
        this.readiness = readiness;
        this.clock = clock;
        this.snapshots = snapshots;
    }

    @Scheduled(
            fixedDelayString = "${app.cycle-readiness-check-ms:60000}",
            initialDelayString = "${app.cycle-readiness-initial-delay-ms:0}")
    public void finalizeStartedCycles() {
        var due = jdbc.queryForList("""
                SELECT r.cycle_id FROM cycle_readiness r
                JOIN evaluation_cycle c ON c.cycle_id = r.cycle_id
                JOIN semester s ON s.semester_id = c.semester_id
                WHERE r.state = 'PENDING' AND s.start_date <= ?
                """, Integer.class, LocalDate.now(clock));
        for (Integer cycleId : due) {
            try {
                readiness.check(cycleId);
            } catch (RuntimeException e) {
                log.error("Could not finalize readiness for cycle {}", cycleId, e);
            }
        }
        var past = jdbc.queryForList("""
                SELECT c.cycle_id FROM evaluation_cycle c JOIN semester s ON s.semester_id = c.semester_id
                WHERE s.end_date < ? AND NOT EXISTS (
                    SELECT 1 FROM cycle_dashboard_snapshot snap WHERE snap.cycle_id = c.cycle_id)
                AND ((s.term = 'Fall' AND s.academic_year = 2026)
                     OR EXISTS (SELECT 1 FROM cycle_professor_roster r WHERE r.cycle_id = c.cycle_id))
                """, Integer.class, LocalDate.now(clock));
        for (Integer cycleId : past) {
            try {
                snapshots.get(cycleId);
            } catch (RuntimeException e) {
                log.error("Could not save historical dashboard for cycle {}", cycleId, e);
            }
        }
    }
}
