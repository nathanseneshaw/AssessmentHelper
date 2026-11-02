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
            int missingObservations) {}

    /**
     * AWAITING_OBSERVER: no observer has accepted yet. MISSING: the observation's date has passed and it is not
     * completed. SCHEDULED: any other observation that is not completed, including one with no date yet.
     */
    public enum Status {
        AWAITING_OBSERVER,
        SCHEDULED,
        COMPLETED,
        MISSING
    }

    /** One generated signup in the cycle and its observation, if an observer has accepted. */
    public record TrackedObservation(
            int signupId,
            int observeeId,
            String observee,
            Integer observerId,
            String observer,
            String course,
            boolean listReleased,
            LocalDate date,
            LocalTime startTime,
            Status status,
            boolean observeeSigned,
            boolean observerSigned,
            LocalDateTime completedAt) {}

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
                .filter(o -> o.status() != Status.AWAITING_OBSERVER)
                .count();
        int missing =
                (int) tracked.stream().filter(o -> o.status() == Status.MISSING).count();
        int fullLists = (int) ac.signups().stream()
                .filter(s -> s.candidates().size() >= ObserverEligibilityService.LIST_SIZE)
                .count();
        int signups = ac.signups().size();
        return new Metrics(
                due.cycleId(),
                due.cycleLabel(),
                due.count(),
                ObserverEligibilityService.LIST_SIZE,
                new Ratio(participating, due.count()),
                new Ratio(observed, signups),
                new Ratio(fullLists, signups),
                missing);
    }

    @Transactional(readOnly = true)
    public Observations observations() {
        var cycle = observers.cycle();
        return new Observations(cycle.cycleId(), cycle.label(), cycle.observationDeadline(), tracked(cycle.cycleId()));
    }

    /**
     * Sends an in-app reminder to the observee and observer of every scheduled or missing observation, and to each
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
            switch (o.status()) {
                case AWAITING_OBSERVER -> {
                    if (!o.listReleased()) continue;
                    sent += notify(
                            o.observeeId(),
                            o,
                            "choose an observer for " + o.course() + " and send a request so the observation can happen"
                                    + deadline + ".",
                            now);
                }
                case SCHEDULED, MISSING -> {
                    String when = o.date() == null
                            ? null
                            : DAY.format(o.date()) + (o.startTime() == null ? "" : " at " + o.startTime());
                    String status = o.status() == Status.MISSING
                            ? " was scheduled for " + when
                                    + " and has not been completed. Complete it as soon as possible."
                            : when == null
                                    ? " does not have a date yet. Schedule it" + deadline + "."
                                    : " is scheduled for " + when + ".";
                    sent += notify(
                            o.observeeId(),
                            o,
                            "your observation of " + o.course() + " by " + o.observer() + status,
                            now);
                    sent += notify(
                            o.observerId(),
                            o,
                            "your observation of " + o.observee() + "'s " + o.course() + status,
                            now);
                }
                case COMPLETED -> {}
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

    // The same signups as ObserverEligibilityService's: generated, not cancelled, in this cycle.
    private List<TrackedObservation> tracked(int cycleId) {
        LocalDate today = LocalDate.now(clock);
        return db.query(
                """
                SELECT es.signup_id, oe.professor_id AS observee_id, p.first_name || ' ' || p.last_name AS observee,
                ob.professor_id AS observer_id, op.first_name || ' ' || op.last_name AS observer,
                c.course_code, c.course_number, s.section_number, ls.released_at, o.observation_id,
                o.observation_date, o.start_time, o.status, o.observee_signed, o.observer_signed, o.completed_at
                FROM evaluation_signup es JOIN observee oe ON oe.observee_id=es.observee_id
                JOIN professor p ON p.professor_id=oe.professor_id
                JOIN course_section s ON s.section_id=es.section_id JOIN course c ON c.course_id=s.course_id
                JOIN observer_list_state ls ON ls.signup_id=es.signup_id
                LEFT JOIN observation o ON o.signup_id=es.signup_id
                LEFT JOIN observer ob ON ob.observer_id=o.observer_id
                LEFT JOIN professor op ON op.professor_id=ob.professor_id
                WHERE oe.cycle_id=? AND es.status<>'CANCELLED' ORDER BY es.signup_id
                """,
                (r, n) -> {
                    LocalDate date = r.getObject("observation_date", LocalDate.class);
                    Status status = r.getObject("observation_id") == null
                            ? Status.AWAITING_OBSERVER
                            : "COMPLETED".equalsIgnoreCase(r.getString("status"))
                                    ? Status.COMPLETED
                                    : date != null && date.isBefore(today) ? Status.MISSING : Status.SCHEDULED;
                    return new TrackedObservation(
                            r.getInt("signup_id"),
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
                            r.getBoolean("observee_signed"),
                            r.getBoolean("observer_signed"),
                            r.getObject("completed_at", LocalDateTime.class));
                },
                cycleId);
    }
}
