package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.entity.ObserverNotification;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationSignupRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserveeRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverNotificationRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    private final EvaluationSignupRepository signupRepository;
    private final ObserveeRepository observeeRepository;
    private final ObserverNotificationRepository notificationRepository;
    private final Clock clock;

    public AcDashboardService(
            EvaluationEligibilityService eligibility,
            ObserverEligibilityService observers,
            EvaluationSignupRepository signupRepository,
            ObserveeRepository observeeRepository,
            ObserverNotificationRepository notificationRepository,
            Clock clock) {
        this.eligibility = eligibility;
        this.observers = observers;
        this.signupRepository = signupRepository;
        this.observeeRepository = observeeRepository;
        this.notificationRepository = notificationRepository;
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
            String surveyStatus,
            Accuracy eligibilityAccuracy) {}

    /** incorrect = observer assignments that break an eligibility rule, out of all assignments checked. */
    public record Accuracy(
            Ratio incorrect, int samePerson, int otherDepartment, int differentLevel, int scheduleConflict) {}

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
        var signedUp = Set.copyOf(signupRepository.findSignedUpProfessorIds(due.cycleId()));
        // Participation Rate: professors who signed up this cycle / professors who were due.
        int participating = signedUp.size();
        var tracked = tracked(due.cycleId());
        // Observer Utilization: distinct professors who took the observer role, from every observation row
        // (a signup can have several), not just the representative row kept in tracked().
        int observerProfessors = (int) trackedRows(due.cycleId()).stream()
                .filter(o -> o.observerId() != null && o.status() != Status.DEFERRED)
                .map(TrackedObservation::observerId)
                .distinct()
                .count();
        // Missing Observation Count: approved appointments (an observer accepted) that did not complete.
        // Signups that never got an observer are reported as unmatched instead.
        int missing = (int) tracked.stream()
                .filter(o -> o.status() == Status.MISSING && o.observerId() != null)
                .count();
        var activeSignups = ac.matchingCases().stream()
                .filter(c -> !"DEFERRED".equals(c.status()))
                .map(ObserverEligibilityService.MatchingCase::signupId)
                .collect(java.util.stream.Collectors.toSet());
        // List Sufficiency: average observer matches per sign-up (total matches / sign-ups).
        int matches = ac.signups().stream()
                .filter(s -> activeSignups.contains(s.signupId()))
                .mapToInt(s -> s.candidates().size())
                .sum();
        int signups = activeSignups.size();
        var counts = observeeRepository.countSurveyCompletion(due.cycleId());
        var survey = new Ratio(counts.submitted(), counts.offered());
        return new Metrics(
                due.cycleId(),
                due.cycleLabel(),
                due.count(),
                ObserverEligibilityService.LIST_SIZE,
                new Ratio(participating, due.count()),
                new Ratio(observerProfessors, signups),
                new Ratio(matches, signups),
                missing,
                survey,
                ProcessFeedbackService.window(
                        ac.cycle().observationDeadline(), ac.cycle().feedbackDeadline(), LocalDate.now(clock)),
                accuracy(observers.audit()));
    }

    private static Accuracy accuracy(ObserverEligibilityService.Audit a) {
        return new Accuracy(
                new Ratio(a.incorrect(), a.checked()),
                a.samePerson(),
                a.otherDepartment(),
                a.differentLevel(),
                a.scheduleConflict());
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
                notify(
                        o.observeeId(),
                        o,
                        "choose an observer for " + o.course() + " and send a request so the observation can happen"
                                + deadline + ".",
                        now);
            } else if (o.status() == Status.PENDING_SIGN_OFF || "Not signed".equals(o.missingReason())) {
                notify(
                        o.observeeId(),
                        o,
                        "review and sign the submitted observation for " + o.course() + deadline + ".",
                        now);
            } else {
                notify(
                        o.observerId(),
                        o,
                        (o.date() == null ? "schedule the observation and submit" : "complete and submit")
                                + " your observation of " + o.observee() + "'s " + o.course() + deadline + ".",
                        now);
            }
            sent++;
        }
        return new Reminders(sent);
    }

    private void notify(int professorId, TrackedObservation o, String reminder, LocalDateTime now) {
        notificationRepository.save(new ObserverNotification(
                null, professorId, o.signupId(), "Reminder from the Assessment Committee: " + reminder, now));
    }

    // One entry per non-cancelled signup. A signup can have several observation rows (several observers may accept);
    // keep the most advanced one so a completed observation is never reported as missing.
    private List<TrackedObservation> tracked(int cycleId) {
        var bySignup = new java.util.LinkedHashMap<Integer, TrackedObservation>();
        for (var o : trackedRows(cycleId)) bySignup.merge(o.signupId(), o, (a, b) -> rank(b) < rank(a) ? b : a);
        return List.copyOf(bySignup.values());
    }

    private static int rank(TrackedObservation o) {
        return switch (o.status()) {
            case COMPLETED -> 0;
            case PENDING_SIGN_OFF -> 1;
            case SCHEDULED -> 2;
            case MISSING -> o.observerId() != null ? 3 : 4;
            case AWAITING_OBSERVER -> 5;
            case DEFERRED -> 6;
        };
    }

    // Every row, including repeated signups when more than one observation exists.
    private List<TrackedObservation> trackedRows(int cycleId) {
        LocalDate today = LocalDate.now(clock);
        return signupRepository.findTrackedObservations(cycleId).stream()
                .map(r -> {
                    LocalDate date = r.observationDate();
                    LocalDate deadline = r.observationDeadline();
                    String stored = r.status();
                    String reason = r.observationId() == null
                            ? "Not scheduled - awaiting observer"
                            : "SUBMITTED".equalsIgnoreCase(stored)
                                    ? "Not signed"
                                    : date == null ? "Not scheduled" : "Not submitted";
                    Status status = "DEFERRED".equals(r.signupStatus())
                            ? Status.DEFERRED
                            : "COMPLETED".equalsIgnoreCase(stored)
                                    ? Status.COMPLETED
                                    : deadline != null && today.isAfter(deadline)
                                            ? Status.MISSING
                                            : "SUBMITTED".equalsIgnoreCase(stored)
                                                    ? Status.PENDING_SIGN_OFF
                                                    : r.observationId() == null
                                                            ? Status.AWAITING_OBSERVER
                                                            : Status.SCHEDULED;
                    return new TrackedObservation(
                            r.signupId(),
                            r.observationId(),
                            r.observeeId(),
                            r.observee(),
                            r.observerId(),
                            r.observer(),
                            r.courseCode() + " " + r.courseNumber() + "." + r.sectionNumber(),
                            r.releasedAt() != null,
                            date,
                            r.startTime(),
                            status,
                            status == Status.MISSING ? reason : null,
                            Boolean.TRUE.equals(r.observeeSigned()),
                            Boolean.TRUE.equals(r.observerSigned()),
                            r.completedAt(),
                            r.deferredAt() == null
                                    ? null
                                    : new EvaluationRecord.Deferral(
                                            r.reason(), r.deferredBy(), r.deferredAt(), r.targetSemester()));
                })
                .toList();
    }
}
