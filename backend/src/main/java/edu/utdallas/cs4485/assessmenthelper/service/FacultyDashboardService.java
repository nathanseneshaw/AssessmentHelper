package edu.utdallas.cs4485.assessmenthelper.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One professor's dashboard for the current cycle. The due date and schedule come from
 * {@link EvaluationEligibilityService#findAll()} and the history from {@link EvaluationRecordService}, so the dashboard
 * agrees with what the committee sees for the same professor.
 */
@Service
public class FacultyDashboardService {
    private final EvaluationEligibilityService eligibility;
    private final ObserverEligibilityService observers;
    private final EvaluationRecordService records;
    private final JdbcTemplate db;
    private final Clock clock;

    public FacultyDashboardService(
            EvaluationEligibilityService eligibility,
            ObserverEligibilityService observers,
            EvaluationRecordService records,
            JdbcTemplate db,
            Clock clock) {
        this.eligibility = eligibility;
        this.observers = observers;
        this.records = records;
        this.db = db;
        this.clock = clock;
    }

    public record Profile(String name, String rank, String department, String school) {}

    /**
     * NOT_DUE: not due or overdue this cycle. NOT_SIGNED_UP: due or overdue with no signup. AWAITING_OBSERVER: signed
     * up and no observer has accepted yet. SCHEDULED: an observer accepted and has not submitted the form yet.
     * SUBMITTED: the observer submitted the form. COMPLETED: the observation record is complete.
     */
    public enum EvaluationStatus {
        NOT_DUE,
        NOT_SIGNED_UP,
        AWAITING_OBSERVER,
        SCHEDULED,
        SUBMITTED,
        COMPLETED
    }

    /** The professor's own evaluation this cycle. Course, observer and dates are null until they exist. */
    public record Evaluation(
            EvaluationStatus status,
            LocalDate signupDeadline,
            String course,
            String observer,
            LocalDate observationDate,
            LocalDateTime completedAt) {}

    /**
     * How often the professor is evaluated and when next. Every field is null for a professor the due list leaves
     * out, such as one whose rank's policy is inactive. dueStatus is the due list's Due, Overdue or Upcoming.
     */
    public record Schedule(
            String rank, Integer intervalYears, LocalDate nextDueDate, String nextDueSemester, String dueStatus) {}

    /**
     * Observations this cycle in which the professor is the observee or the observer. completed counts submitted ones
     * too, since submitting is the observer's last step.
     */
    public record TermObservations(int total, int completed, int scheduled) {}

    public enum Role {
        OBSERVEE,
        OBSERVER
    }

    /** Where the observation form is. NOT_STARTED covers an accepted or scheduled observation the observer has not begun. */
    public enum ObservationStatus {
        NOT_STARTED,
        IN_PROGRESS,
        SUBMITTED,
        COMPLETED;

        /** Whether the observer's part is over: the form is submitted, or the record is complete. */
        public boolean done() {
            return this == SUBMITTED || this == COMPLETED;
        }
    }

    /**
     * An observation the professor is part of. {@code with} is the other professor: the observer or the observee.
     * overdue is true once the date has passed and the form is still not submitted.
     */
    public record ObservationItem(
            int observationId,
            int cycleId,
            String semester,
            Role role,
            String course,
            String with,
            ObservationStatus status,
            boolean overdue,
            LocalDate date,
            LocalTime startTime,
            LocalDateTime completedAt) {}

    /**
     * SIGNUP: sign up by the cycle's signup deadline. OBSERVATION: an observation this cycle whose form is not submitted
     * yet. FEEDBACK: process feedback on a submitted or completed observation this cycle, due by the cycle's feedback
     * deadline.
     */
    public enum UpcomingType {
        SIGNUP,
        OBSERVATION,
        FEEDBACK
    }

    /** One action item. The observation fields are null for SIGNUP. overdue is true once the date has passed. */
    public record Upcoming(
            UpcomingType type,
            LocalDate date,
            LocalTime startTime,
            boolean overdue,
            Integer observationId,
            Role role,
            ObservationStatus status,
            String course,
            String with) {}

    public record Dashboard(
            int cycleId,
            String cycleLabel,
            Profile profile,
            Evaluation evaluation,
            Schedule schedule,
            TermObservations termObservations,
            List<Upcoming> upcoming,
            List<EvaluationRecord> history,
            List<ObservationItem> observationsForMe,
            List<ObservationItem> observationsForOthers) {}

    /** An observation, and whether the professor has given process feedback on it. */
    private record Row(ObservationItem item, boolean feedbackGiven) {}

    @Transactional(readOnly = true)
    public Dashboard dashboard(int professorId) {
        LocalDate today = LocalDate.now(clock);
        var cycle = observers.cycle();
        // Throws NotFoundException for an unknown professor before anything else is loaded.
        var history = records.findByProfessor(professorId).stream()
                .filter(r -> r.cycleId() != cycle.cycleId())
                .toList();
        var due = eligibility.findAll().faculty().stream()
                .filter(f -> f.professorId() == professorId)
                .findFirst();
        Schedule schedule = due.map(f ->
                        new Schedule(f.rank(), f.intervalYears(), f.nextDueDate(), f.nextDueSemester(), f.status()))
                .orElse(new Schedule(null, null, null, null, null));

        var rows = observations(professorId, today);
        var thisCycle =
                rows.stream().filter(r -> r.item().cycleId() == cycle.cycleId()).toList();
        int completed =
                (int) thisCycle.stream().filter(r -> r.item().status().done()).count();
        var termObservations = new TermObservations(thisCycle.size(), completed, thisCycle.size() - completed);

        var own =
                thisCycle.stream().filter(r -> r.item().role() == Role.OBSERVEE).findFirst();
        Evaluation evaluation;
        if (own.isPresent()) {
            var o = own.get().item();
            evaluation = new Evaluation(
                    switch (o.status()) {
                        case COMPLETED -> EvaluationStatus.COMPLETED;
                        case SUBMITTED -> EvaluationStatus.SUBMITTED;
                        case NOT_STARTED, IN_PROGRESS -> EvaluationStatus.SCHEDULED;
                    },
                    cycle.signupDeadline(),
                    o.course(),
                    o.with(),
                    o.date(),
                    o.completedAt());
        } else {
            String signedUp = signupCourse(professorId, cycle.cycleId());
            boolean needed = due.map(f -> !"Upcoming".equals(f.status())).orElse(false);
            evaluation = new Evaluation(
                    signedUp != null
                            ? EvaluationStatus.AWAITING_OBSERVER
                            : needed ? EvaluationStatus.NOT_SIGNED_UP : EvaluationStatus.NOT_DUE,
                    cycle.signupDeadline(),
                    signedUp,
                    null,
                    null,
                    null);
        }

        List<Upcoming> upcoming = new ArrayList<>();
        if (evaluation.status() == EvaluationStatus.NOT_SIGNED_UP && cycle.signupOpen())
            upcoming.add(new Upcoming(
                    UpcomingType.SIGNUP, cycle.signupDeadline(), null, false, null, null, null, null, null));
        for (var r : thisCycle) {
            var o = r.item();
            if (!o.status().done())
                upcoming.add(new Upcoming(
                        UpcomingType.OBSERVATION,
                        o.date(),
                        o.startTime(),
                        o.overdue(),
                        o.observationId(),
                        o.role(),
                        o.status(),
                        o.course(),
                        o.with()));
            else if (!r.feedbackGiven())
                upcoming.add(new Upcoming(
                        UpcomingType.FEEDBACK,
                        cycle.feedbackDeadline(),
                        null,
                        cycle.feedbackDeadline() != null
                                && cycle.feedbackDeadline().isBefore(today),
                        o.observationId(),
                        o.role(),
                        o.status(),
                        o.course(),
                        o.with()));
        }
        // Soonest first; items without a date yet go last.
        upcoming.sort(Comparator.comparing(Upcoming::date, Comparator.nullsLast(Comparator.naturalOrder())));

        return new Dashboard(
                cycle.cycleId(),
                cycle.label(),
                profile(professorId),
                evaluation,
                schedule,
                termObservations,
                List.copyOf(upcoming),
                history,
                rows.stream()
                        .map(Row::item)
                        .filter(o -> o.role() == Role.OBSERVEE)
                        .toList(),
                rows.stream()
                        .map(Row::item)
                        .filter(o -> o.role() == Role.OBSERVER)
                        .toList());
    }

    private Profile profile(int professorId) {
        return db.queryForObject(
                """
                SELECT p.first_name || ' ' || p.last_name AS name, p.professor_rank, d.department_name, s.school_name
                FROM professor p JOIN department d ON d.department_id=p.department_id
                JOIN school s ON s.school_id=d.school_id WHERE p.professor_id=?
                """,
                (r, n) -> new Profile(
                        r.getString("name"),
                        r.getString("professor_rank"),
                        r.getString("department_name"),
                        r.getString("school_name")),
                professorId);
    }

    /** The course of the professor's signup this cycle, or null if they have not signed up. */
    private String signupCourse(int professorId, int cycleId) {
        return db.queryForList("""
                        SELECT c.course_code || ' ' || c.course_number || '.' || s.section_number
                        FROM evaluation_signup es JOIN observee oe ON oe.observee_id=es.observee_id
                        JOIN course_section s ON s.section_id=es.section_id JOIN course c ON c.course_id=s.course_id
                        WHERE oe.professor_id=? AND oe.cycle_id=? AND es.status<>'CANCELLED'
                        """, String.class, professorId, cycleId).stream()
                .findFirst()
                .orElse(null);
    }

    // Newest first; an observation with no date yet comes before every dated one.
    private List<Row> observations(int professorId, LocalDate today) {
        return db.query(
                """
                SELECT o.observation_id, o.observation_date, o.start_time, o.status, o.completed_at, oe.cycle_id,
                sm.term, sm.academic_year, pe.professor_id AS observee_id,
                pe.first_name || ' ' || pe.last_name AS observee, po.first_name || ' ' || po.last_name AS observer,
                c.course_code, c.course_number, s.section_number,
                EXISTS (SELECT 1 FROM process_feedback f WHERE f.signup_id=o.signup_id AND f.professor_id=?)
                    AS feedback_given
                FROM observation o JOIN evaluation_signup es ON es.signup_id=o.signup_id
                JOIN observee oe ON oe.observee_id=es.observee_id JOIN professor pe ON pe.professor_id=oe.professor_id
                JOIN observer ob ON ob.observer_id=o.observer_id JOIN professor po ON po.professor_id=ob.professor_id
                JOIN course_section s ON s.section_id=es.section_id JOIN course c ON c.course_id=s.course_id
                JOIN evaluation_cycle ec ON ec.cycle_id=oe.cycle_id JOIN semester sm ON sm.semester_id=ec.semester_id
                WHERE (pe.professor_id=? OR po.professor_id=?) AND es.status<>'CANCELLED'
                ORDER BY o.observation_date DESC NULLS FIRST, o.observation_id DESC
                """,
                (r, n) -> {
                    LocalDate date = r.getObject("observation_date", LocalDate.class);
                    String stored = r.getString("status");
                    var status = switch (stored == null ? "" : stored.toUpperCase(Locale.ROOT)) {
                        case "COMPLETED" -> ObservationStatus.COMPLETED;
                        case "SUBMITTED" -> ObservationStatus.SUBMITTED;
                        case "IN_PROGRESS" -> ObservationStatus.IN_PROGRESS;
                        default -> ObservationStatus.NOT_STARTED;
                    };
                    boolean observee = r.getInt("observee_id") == professorId;
                    var item = new ObservationItem(
                            r.getInt("observation_id"),
                            r.getInt("cycle_id"),
                            r.getString("term") + " " + r.getInt("academic_year"),
                            observee ? Role.OBSERVEE : Role.OBSERVER,
                            r.getString("course_code") + " " + r.getString("course_number") + "."
                                    + r.getString("section_number"),
                            r.getString(observee ? "observer" : "observee"),
                            status,
                            !status.done() && date != null && date.isBefore(today),
                            date,
                            r.getObject("start_time", LocalTime.class),
                            r.getObject("completed_at", LocalDateTime.class));
                    return new Row(item, r.getBoolean("feedback_given"));
                },
                professorId,
                professorId,
                professorId);
    }
}
