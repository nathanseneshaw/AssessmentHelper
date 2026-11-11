package edu.utdallas.cs4485.assessmenthelper.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The committee dashboard's counts and observation tracking for the current cycle. Due faculty come from
 * {@link EvaluationEligibilityService#findDue()} and the cycle's signups from {@link ObserverEligibilityService#acState()},
 * so the numbers agree with the due list and the observer matching screen.
 */
@Service
public class AcDashboardService {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US);

    private final EvaluationEligibilityService eligibility;
    private final ObserverEligibilityService observers;
    private final JdbcTemplate db;
    private final Clock clock;

    public AcDashboardService(
            EvaluationEligibilityService eligibility,
            ObserverEligibilityService observers,
            JdbcTemplate db,
            Clock clock) {
        this.eligibility = eligibility;
        this.observers = observers;
        this.db = db;
        this.clock = clock;
    }

    public record Ratio(int count, int total) {}

    public record Metrics(
            int cycleId,
            String cycleLabel,
            int facultyDue,
            int listSize,
            Ratio participation,
            Ratio observerUtilization,
            Ratio listSufficiency,
            int missingObservations,
            Ratio surveyCompletion,
            String surveyStatus) {}

    /**
     * AWAITING_OBSERVER: no observer has accepted yet. PENDING_SIGN_OFF: submitted but unsigned.
     * MISSING: the cycle observation deadline has passed without final completion.
     */
    public enum Status {
        DEFERRED,
        AWAITING_OBSERVER,
        SCHEDULED,
        PENDING_SIGN_OFF,
        COMPLETED,
        MISSING
    }

    /** One generated signup in the cycle and its observation, if an observer has accepted. */
    public record TrackedObservation(
            int signupId,
            Integer observationId,
            int observeeId,
            String observee,
            Integer observerId,
            String observer,
            String course,
            boolean listReleased,
            LocalDate date,
            LocalTime startTime,
            Status status,
            String missingReason,
            boolean observeeSigned,
            boolean observerSigned,
            LocalDateTime completedAt,

            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            EvaluationRecord.Deferral deferral) {}

    public record Observations(
            int cycleId, String cycleLabel, LocalDate observationDeadline, List<TrackedObservation> observations) {}

    public record Reminders(int sent) {}

    @Transactional(readOnly = true)
    public Metrics metrics() {
        var due = eligibility.findDue();
        var ac = observers.acState();
        if (ac.cycle().cycleId() != due.cycleId())
            throw new ConflictException("The evaluation cycle changed. Refresh the dashboard.");
        var signedUp = Set.copyOf(db.queryForList("""
                SELECT oe.professor_id FROM evaluation_signup es JOIN observee oe ON oe.observee_id=es.observee_id
                WHERE oe.cycle_id=? AND es.status<>'CANCELLED'
                """, Integer.class, due.cycleId()));
        int participating = (int) due.faculty().stream()
                .filter(f -> signedUp.contains(f.professorId()))
                .count();
        var tracked = tracked(due.cycleId());
        int observed = (int) tracked.stream()
                .filter(o -> o.observerId() != null && o.status() != Status.DEFERRED)
                .count();
        int missing =
                (int) tracked.stream().filter(o -> o.status() == Status.MISSING).count();
        var activeSignups = ac.matchingCases().stream()
                .filter(c -> !"DEFERRED".equals(c.status()))
                .map(ObserverEligibilityService.MatchingCase::signupId)
                .collect(java.util.stream.Collectors.toSet());
        int fullLists = (int) ac.signups().stream()
                .filter(s -> activeSignups.contains(s.signupId()))
                .filter(s -> s.candidates().size() >= ObserverEligibilityService.LIST_SIZE)
                .count();
        int signups = activeSignups.size();
        var survey =
                db.queryForObject("""
                SELECT COUNT(DISTINCT oe.professor_id) AS offered, COUNT(DISTINCT f.professor_id) AS submitted
                FROM observee oe
                LEFT JOIN process_feedback f ON f.cycle_id=oe.cycle_id AND f.professor_id=oe.professor_id
                WHERE oe.cycle_id=? AND oe.status IN ('ACTIVE', 'COMPLETED')
                """, (r, n) -> new Ratio(r.getInt("submitted"), r.getInt("offered")), due.cycleId());
        return new Metrics(
                due.cycleId(),
                due.cycleLabel(),
                due.count(),
                ObserverEligibilityService.LIST_SIZE,
                new Ratio(participating, due.count()),
                new Ratio(observed, signups),
                new Ratio(fullLists, signups),
                missing,
                survey,
                ProcessFeedbackService.window(
                        ac.cycle().observationDeadline(), ac.cycle().feedbackDeadline(), LocalDate.now(clock)));
    }

    @Transactional(readOnly = true)
    public Observations observations() {
        var cycle = observers.cycle();
        return new Observations(cycle.cycleId(), cycle.label(), cycle.observationDeadline(), tracked(cycle.cycleId()));
    }

    /**
     * Sends submission reminders to observers, sign-off reminders to observees, and scheduling reminders to each
     * observee whose released list still has no accepted observer. Lists the committee has not released are skipped,
     * since their observees cannot request anyone yet.
     */
    @Transactional
    public Reminders remind() {
        var cycle = observers.cycle();
        String deadline = cycle.observationDeadline() == null
                ? ""
                : " by the observation deadline, " + DAY.format(cycle.observationDeadline());
        LocalDateTime now = LocalDateTime.now(clock);
        int sent = 0;
        for (var o : tracked(cycle.cycleId())) {
            if (o.status() == Status.COMPLETED || o.status() == Status.DEFERRED) continue;
            if (o.observerId() == null) {
                if (!o.listReleased()) continue;
                sent += notify(
                        o.observeeId(),
                        o,
                        "choose an observer for " + o.course() + " and send a request so the observation can happen"
                                + deadline + ".",
                        now);
            } else if (o.status() == Status.PENDING_SIGN_OFF || "Not signed".equals(o.missingReason())) {
                sent += notify(
                        o.observeeId(),
                        o,
                        "review and sign the submitted observation for " + o.course() + deadline + ".",
                        now);
            } else {
                sent += notify(
                        o.observerId(),
                        o,
                        (o.date() == null ? "schedule the observation and submit" : "complete and submit")
                                + " your observation of " + o.observee() + "'s " + o.course() + deadline + ".",
                        now);
            }
        }
        return new Reminders(sent);
    }

    private int notify(int professorId, TrackedObservation o, String reminder, LocalDateTime now) {
        return db.update(
                "INSERT INTO observer_notification(professor_id,signup_id,message,created_at) VALUES (?,?,?,?)",
                professorId,
                o.signupId(),
                "Reminder from the Assessment Committee: " + reminder,
                now);
    }

    // Track all non-cancelled signups, including those awaiting a generated observer list.
    private List<TrackedObservation> tracked(int cycleId) {
        LocalDate today = LocalDate.now(clock);
        return db.query(
                """
                SELECT es.signup_id, oe.professor_id AS observee_id, p.first_name || ' ' || p.last_name AS observee,
                ob.professor_id AS observer_id, op.first_name || ' ' || op.last_name AS observer,
                c.course_code, c.course_number, s.section_number, ls.released_at, o.observation_id,
                o.observation_date, o.start_time, o.status, o.observee_signed, o.observer_signed, o.completed_at,
                es.status AS signup_status, ec.observation_deadline, d.reason, d.deferred_at,
                dp.first_name || ' ' || dp.last_name AS deferred_by,
                ds.term || ' ' || ds.academic_year AS target_semester
                FROM evaluation_signup es JOIN observee oe ON oe.observee_id=es.observee_id
                JOIN evaluation_cycle ec ON ec.cycle_id=oe.cycle_id
                JOIN professor p ON p.professor_id=oe.professor_id
                JOIN course_section s ON s.section_id=es.section_id JOIN course c ON c.course_id=s.course_id
                LEFT JOIN observer_list_state ls ON ls.signup_id=es.signup_id
                LEFT JOIN observation o ON o.signup_id=es.signup_id
                LEFT JOIN observer ob ON ob.observer_id=o.observer_id
                LEFT JOIN professor op ON op.professor_id=ob.professor_id
                LEFT JOIN evaluation_deferral d ON d.signup_id=es.signup_id
                LEFT JOIN professor dp ON dp.professor_id=d.deferred_by
                LEFT JOIN semester ds ON ds.semester_id=d.target_semester_id
                WHERE oe.cycle_id=? AND es.status<>'CANCELLED' ORDER BY es.signup_id
                """,
                (r, n) -> {
                    LocalDate date = r.getObject("observation_date", LocalDate.class);
                    LocalDate deadline = r.getObject("observation_deadline", LocalDate.class);
                    String stored = r.getString("status");
                    String reason = r.getObject("observation_id") == null
                            ? "Not scheduled - awaiting observer"
                            : "SUBMITTED".equalsIgnoreCase(stored)
                                    ? "Not signed"
                                    : date == null ? "Not scheduled" : "Not submitted";
                    Status status = "DEFERRED".equals(r.getString("signup_status"))
                            ? Status.DEFERRED
                            : "COMPLETED".equalsIgnoreCase(stored)
                                    ? Status.COMPLETED
                                    : deadline != null && today.isAfter(deadline)
                                            ? Status.MISSING
                                            : "SUBMITTED".equalsIgnoreCase(stored)
                                                    ? Status.PENDING_SIGN_OFF
                                                    : r.getObject("observation_id") == null
                                                            ? Status.AWAITING_OBSERVER
                                                            : Status.SCHEDULED;
                    return new TrackedObservation(
                            r.getInt("signup_id"),
                            r.getObject("observation_id", Integer.class),
                            r.getInt("observee_id"),
                            r.getString("observee"),
                            r.getObject("observer_id", Integer.class),
                            r.getString("observer"),
                            r.getString("course_code") + " " + r.getString("course_number") + "."
                                    + r.getString("section_number"),
                            r.getTimestamp("released_at") != null,
                            date,
                            r.getObject("start_time", LocalTime.class),
                            status,
                            status == Status.MISSING ? reason : null,
                            r.getBoolean("observee_signed"),
                            r.getBoolean("observer_signed"),
                            r.getObject("completed_at", LocalDateTime.class),
                            r.getObject("deferred_at") == null
                                    ? null
                                    : new EvaluationRecord.Deferral(
                                            r.getString("reason"),
                                            r.getString("deferred_by"),
                                            r.getObject("deferred_at", LocalDateTime.class),
                                            r.getString("target_semester")));
                },
                cycleId);
    }
}
