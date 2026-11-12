package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationSignupRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
    private final ProfessorRepository professorRepository;
    private final EvaluationSignupRepository signupRepository;
    private final ObservationRepository observationRepository;
    private final Clock clock;

    public FacultyDashboardService(
            EvaluationEligibilityService eligibility,
            ObserverEligibilityService observers,
            EvaluationRecordService records,
            ProfessorRepository professorRepository,
            EvaluationSignupRepository signupRepository,
            ObservationRepository observationRepository,
            Clock clock) {
        this.eligibility = eligibility;
        this.observers = observers;
        this.records = records;
        this.professorRepository = professorRepository;
        this.signupRepository = signupRepository;
        this.observationRepository = observationRepository;
        this.clock = clock;
    }

    public record Profile(String name, String rank, String department, String school) {}

    /**
     * NOT_DUE: not due or overdue this cycle. NOT_SIGNED_UP: due or overdue with no signup. AWAITING_OBSERVER: signed
     * up and no observer has accepted yet. SCHEDULED: an observer accepted and has not submitted the form yet.
     * SUBMITTED: the observer submitted the form. COMPLETED: the observation record is complete.
     */
    public enum EvaluationStatus {
        DEFERRED,
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
     * Observations this cycle in which the professor is the observee or the observer. completed counts only fully signed records; pendingSignOff counts submitted forms.
     */
    public record TermObservations(int total, int completed, int scheduled, int pendingSignOff) {}

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
     * overdue is true after the cycle observation deadline until final completion.
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
     * deadline. SIGN_OFF: a submitted observation the observee has not signed off yet.
     */
    public enum UpcomingType {
        SIGNUP,
        OBSERVATION,
        FEEDBACK,
        SIGN_OFF
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
        int completed = (int) thisCycle.stream()
                .filter(r -> r.item().status() == ObservationStatus.COMPLETED)
                .count();
        int pending = (int) thisCycle.stream()
                .filter(r -> r.item().status() == ObservationStatus.SUBMITTED)
                .count();
        var termObservations =
                new TermObservations(thisCycle.size(), completed, thisCycle.size() - completed - pending, pending);

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
                    due.map(f -> "Due next semester".equals(f.status())).orElse(false)
                            ? EvaluationStatus.DEFERRED
                            : signedUp != null
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
            else {
                if (o.role() == Role.OBSERVEE && o.status() == ObservationStatus.SUBMITTED)
                    upcoming.add(new Upcoming(
                            UpcomingType.SIGN_OFF,
                            cycle.observationDeadline(),
                            null,
                            o.overdue(),
                            o.observationId(),
                            o.role(),
                            o.status(),
                            o.course(),
                            o.with()));
                if (!r.feedbackGiven())
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
        var r = professorRepository.findDashboardProfile(professorId).orElseThrow();
        return new Profile(r.name(), r.professorRank(), r.departmentName(), r.schoolName());
    }

    /** The course of the professor's signup this cycle, or null if they have not signed up. */
    private String signupCourse(int professorId, int cycleId) {
        return signupRepository.findSignupCourseLabels(professorId, cycleId).stream()
                .findFirst()
                .orElse(null);
    }

    // Newest first; an observation with no date yet comes before every dated one.
    private List<Row> observations(int professorId, LocalDate today) {
        return observationRepository.findDashboardObservations(professorId).stream()
                .map(r -> {
                    LocalDate date = r.observationDate();
                    String stored = r.status();
                    var status = switch (stored == null ? "" : stored.toUpperCase(Locale.ROOT)) {
                        case "COMPLETED" -> ObservationStatus.COMPLETED;
                        case "SUBMITTED" -> ObservationStatus.SUBMITTED;
                        case "IN_PROGRESS" -> ObservationStatus.IN_PROGRESS;
                        default -> ObservationStatus.NOT_STARTED;
                    };
                    boolean observee = r.observeeId() == professorId;
                    var item = new ObservationItem(
                            r.observationId(),
                            r.cycleId(),
                            r.term() + " " + r.academicYear(),
                            observee ? Role.OBSERVEE : Role.OBSERVER,
                            r.courseCode() + " " + r.courseNumber() + "." + r.sectionNumber(),
                            observee ? r.observer() : r.observee(),
                            status,
                            status != ObservationStatus.COMPLETED
                                    && r.observationDeadline() != null
                                    && today.isAfter(r.observationDeadline()),
                            date,
                            r.startTime(),
                            r.completedAt());
                    return new Row(item, Boolean.TRUE.equals(r.feedbackGiven()));
                })
                .toList();
    }
}
