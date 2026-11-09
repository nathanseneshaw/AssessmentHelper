package edu.utdallas.cs4485.assessmenthelper.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MissingObservationService {
    private final JdbcTemplate db;
    private final EvaluationDeadlineService deadlines;
    private final ObserverEligibilityService eligibility;
    private final ObservationService observations;
    private final Clock clock;

    public MissingObservationService(
            JdbcTemplate db,
            EvaluationDeadlineService deadlines,
            ObserverEligibilityService eligibility,
            ObservationService observations,
            Clock clock) {
        this.db = db;
        this.deadlines = deadlines;
        this.eligibility = eligibility;
        this.observations = observations;
        this.clock = clock;
    }

    public record Alert(String kind, Integer observationId, LocalDate dueDate, String message) {}

    /**
     * A signup with no completed observation. {@code observer} and {@code observationStatus} are null when no
     * observer has accepted the signup yet.
     */
    public record MissingSignup(
            int signupId, int observeeId, String observee, String course, String observer, String observationStatus) {}

    /** The current cycle's signups that are past its observation deadline without a completed observation. */
    public record MissingSignups(
            int cycleId, String cycleLabel, LocalDate observationDeadline, List<MissingSignup> signups) {}

    /**
     * Committee view of the current cycle: every non-cancelled signup without a COMPLETED observation, once the
     * observation deadline has passed (the deadline day itself is still on time). Before then, or when the cycle has
     * no deadline, the list is empty. A SUBMITTED observation still counts, since the record is not complete until
     * the observee signs off.
     */
    @Transactional(readOnly = true)
    public MissingSignups findMissingSignups() {
        var cycle = eligibility.cycle();
        LocalDate deadline = cycle.observationDeadline();
        if (deadline == null || !LocalDate.now(clock).isAfter(deadline)) {
            return new MissingSignups(cycle.cycleId(), cycle.label(), deadline, List.of());
        }
        List<MissingSignup> rows = db.query("""
                SELECT es.signup_id, oe.professor_id AS observee_id, p.first_name || ' ' || p.last_name AS observee,
                c.course_code, c.course_number, s.section_number,
                op.first_name || ' ' || op.last_name AS observer, o.status
                FROM evaluation_signup es JOIN observee oe ON oe.observee_id=es.observee_id
                JOIN professor p ON p.professor_id=oe.professor_id
                JOIN course_section s ON s.section_id=es.section_id JOIN course c ON c.course_id=s.course_id
                LEFT JOIN observation o ON o.signup_id=es.signup_id
                LEFT JOIN observer ob ON ob.observer_id=o.observer_id
                LEFT JOIN professor op ON op.professor_id=ob.professor_id
                WHERE oe.cycle_id=? AND es.status<>'CANCELLED'
                AND NOT EXISTS (SELECT 1 FROM observation d WHERE d.signup_id=es.signup_id
                AND UPPER(d.status)='COMPLETED')
                ORDER BY p.last_name, p.first_name, es.signup_id, o.observation_id
                """, this::missingSignup, cycle.cycleId());
        // A signup can have one observation per observer, so it can come back more than once; the last row wins.
        var latest = new LinkedHashMap<Integer, MissingSignup>();
        for (var row : rows) {
            latest.put(row.signupId(), row);
        }
        return new MissingSignups(cycle.cycleId(), cycle.label(), deadline, List.copyOf(latest.values()));
    }

    private MissingSignup missingSignup(ResultSet rs, int row) throws SQLException {
        String course = rs.getString("course_code") + " " + rs.getString("course_number");
        return new MissingSignup(
                rs.getInt("signup_id"),
                rs.getInt("observee_id"),
                rs.getString("observee"),
                course + "." + rs.getString("section_number"),
                rs.getString("observer"),
                rs.getString("status"));
    }

    @Transactional(readOnly = true)
    public List<Alert> findByProfessor(int professorId) {
        var alerts = new ArrayList<Alert>();
        LocalDate today = LocalDate.now(clock);
        // Observer obligations remain visible even when their own evaluation is not due.
        for (var observation : observations.professorObservations(professorId)) {
            if (observation.role() == ObservationService.ViewerRole.OBSERVER
                    && observation.submittedAt() == null
                    && (observation.status() == ObservationService.Status.SCHEDULED
                            || observation.status() == ObservationService.Status.ACCEPTED
                            || observation.status() == ObservationService.Status.IN_PROGRESS)
                    && observation.observationDate() != null
                    && today.isAfter(observation.observationDate())) {
                alerts.add(new Alert(
                        "LATE_SUBMISSION",
                        observation.observationId(),
                        observation.observationDate(),
                        "Observation for " + observation.observeeName() + " (" + observation.courseLabel()
                                + ") is late. Complete and submit your observation."));
            }
        }
        var cycle = eligibility.cycle();
        // Missing policy/hire data is not proof of a missed evaluation.
        int applicable = db.queryForObject("""
                SELECT COUNT(*) FROM professor p JOIN evaluation_policy ep ON ep.professor_rank=p.professor_rank
                WHERE p.professor_id=? AND p.active_status=TRUE AND ep.active_status=TRUE
                """, Integer.class, professorId);
        if (applicable == 0) return alerts;
        var deadline = deadlines.findByProfessor(professorId);
        LocalDate semesterEnd = db.queryForObject(
                "SELECT end_date FROM semester WHERE semester_id=?", LocalDate.class, cycle.semesterId());
        boolean required = deadline.nextDueDate() != null
                && (deadline.overdue() || !deadline.nextDueDate().isAfter(semesterEnd));
        if (!required) return alerts;
        int signups = db.queryForObject("""
                SELECT COUNT(*) FROM evaluation_signup es JOIN observee oe ON oe.observee_id=es.observee_id
                WHERE oe.professor_id=? AND oe.cycle_id=? AND es.status<>'CANCELLED'
                """, Integer.class, professorId, cycle.cycleId());
        if (signups == 0 && cycle.signupDeadline() != null && today.isAfter(cycle.signupDeadline())) {
            alerts.add(
                    new Alert(
                            "MISSING_SIGNUP",
                            null,
                            cycle.signupDeadline(),
                            "Your required evaluation has no signup for " + cycle.label()
                                    + ". The signup deadline has passed. Contact the Assessment Committee to arrange your evaluation."));
        } else if (signups > 0 && cycle.observationDeadline() != null && today.isAfter(cycle.observationDeadline())) {
            int scheduled = db.queryForObject("""
                    SELECT COUNT(*) FROM observation o JOIN evaluation_signup es ON es.signup_id=o.signup_id
                    JOIN observee oe ON oe.observee_id=es.observee_id
                    WHERE oe.professor_id=? AND oe.cycle_id=? AND es.status<>'CANCELLED'
                    """, Integer.class, professorId, cycle.cycleId());
            if (scheduled == 0)
                alerts.add(new Alert(
                        "MISSING_SCHEDULE",
                        null,
                        cycle.observationDeadline(),
                        "Your required evaluation has no confirmed observation for " + cycle.label()
                                + ". Contact the Assessment Committee to arrange an observation."));
        }
        return alerts;
    }
}
