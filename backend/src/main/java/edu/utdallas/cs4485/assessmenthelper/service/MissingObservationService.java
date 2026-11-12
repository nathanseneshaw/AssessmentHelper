package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationSignupRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationSignupRepository.MissingSignupRow;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.SemesterRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MissingObservationService {
    private final EvaluationSignupRepository signupRepository;
    private final ObservationRepository observationRepository;
    private final ProfessorRepository professorRepository;
    private final SemesterRepository semesterRepository;
    private final EvaluationDeadlineService deadlines;
    private final ObserverEligibilityService eligibility;
    private final ObservationService observations;
    private final Clock clock;

    public MissingObservationService(
            EvaluationSignupRepository signupRepository,
            ObservationRepository observationRepository,
            ProfessorRepository professorRepository,
            SemesterRepository semesterRepository,
            EvaluationDeadlineService deadlines,
            ObserverEligibilityService eligibility,
            ObservationService observations,
            Clock clock) {
        this.signupRepository = signupRepository;
        this.observationRepository = observationRepository;
        this.professorRepository = professorRepository;
        this.semesterRepository = semesterRepository;
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
        List<MissingSignup> rows = signupRepository.findMissingSignups(cycle.cycleId()).stream()
                .map(this::missingSignup)
                .toList();
        // The last row is the newest submitted observation, or the newest observation if none were submitted.
        var representative = new LinkedHashMap<Integer, MissingSignup>();
        for (var row : rows) {
            representative.put(row.signupId(), row);
        }
        return new MissingSignups(cycle.cycleId(), cycle.label(), deadline, List.copyOf(representative.values()));
    }

    private MissingSignup missingSignup(MissingSignupRow row) {
        String course = row.courseCode() + " " + row.courseNumber();
        return new MissingSignup(
                row.signupId(),
                row.observeeId(),
                row.observee(),
                course + "." + row.sectionNumber(),
                row.observer(),
                row.status());
    }

    @Transactional(readOnly = true)
    public List<Alert> findByProfessor(int professorId) {
        var alerts = new ArrayList<Alert>();
        LocalDate today = LocalDate.now(clock);
        var cycle = eligibility.cycle();
        // Use each observation's cycle deadline, including obligations from earlier cycles.
        var observationDeadlines = new java.util.HashMap<Integer, LocalDate>();
        for (var row : observationRepository.findObservationDeadlines(professorId))
            observationDeadlines.put(row.observationId(), row.observationDeadline());
        for (var observation : observations.professorObservations(professorId)) {
            LocalDate due = observationDeadlines.get(observation.observationId());
            if (due == null || !today.isAfter(due) || observation.status() == ObservationService.Status.COMPLETED)
                continue;
            boolean pendingSignOff = observation.status() == ObservationService.Status.SUBMITTED;
            boolean observer = observation.role() == ObservationService.ViewerRole.OBSERVER;
            if (observer && !pendingSignOff) {
                alerts.add(new Alert(
                        "LATE_SUBMISSION",
                        observation.observationId(),
                        due,
                        "Missing: " + (observation.observationDate() == null ? "Not scheduled" : "Not submitted")
                                + ". Observation for " + observation.observeeName() + " (" + observation.courseLabel()
                                + "). Complete and submit your observation."));
            } else if (!observer) {
                alerts.add(new Alert(
                        pendingSignOff ? "MISSING_SIGN_OFF" : "MISSING_SUBMISSION",
                        observation.observationId(),
                        due,
                        "Missing: "
                                + (pendingSignOff
                                        ? "Not signed"
                                        : observation.observationDate() == null ? "Not scheduled" : "Not submitted")
                                + ". Observation of " + observation.courseLabel() + ". "
                                + (pendingSignOff
                                        ? "Review and sign your observation."
                                        : "Contact your observer to complete the observation.")));
            }
        }
        // Missing policy/hire data is not proof of a missed evaluation.
        int applicable = professorRepository.countApplicableEvaluationPolicies(professorId);
        if (applicable == 0) return alerts;
        var deadline = deadlines.findByProfessor(professorId);
        LocalDate semesterEnd =
                semesterRepository.findById(cycle.semesterId()).orElseThrow().endDate();
        boolean required = deadline.nextDueDate() != null
                && (deadline.overdue() || !deadline.nextDueDate().isAfter(semesterEnd));
        if (!required) return alerts;
        int signups = signupRepository.countActiveSignupsInCycle(professorId, cycle.cycleId());
        if (signups == 0 && cycle.signupDeadline() != null && today.isAfter(cycle.signupDeadline())) {
            alerts.add(
                    new Alert(
                            "MISSING_SIGNUP",
                            null,
                            cycle.signupDeadline(),
                            "Your required evaluation has no signup for " + cycle.label()
                                    + ". The signup deadline has passed. Contact the Assessment Committee to arrange your evaluation."));
        } else if (signups > 0 && cycle.observationDeadline() != null && today.isAfter(cycle.observationDeadline())) {
            int scheduled = observationRepository.countActiveObservationsInCycle(professorId, cycle.cycleId());
            if (scheduled == 0)
                alerts.add(new Alert(
                        "MISSING_SCHEDULE",
                        null,
                        cycle.observationDeadline(),
                        "Missing: Not scheduled. Your required evaluation has no confirmed observation for "
                                + cycle.label() + ". Contact the Assessment Committee to arrange an observation."));
        }
        return alerts;
    }
}
