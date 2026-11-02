package edu.utdallas.cs4485.assessmenthelper.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
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
