package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationCycle;
import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationNotification;
import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationPolicy;
import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationSignup;
import edu.utdallas.cs4485.assessmenthelper.entity.Observation;
import edu.utdallas.cs4485.assessmenthelper.entity.Observee;
import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.entity.Semester;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationCycleRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationNotificationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationPolicyRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationSignupRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserveeRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.SemesterRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EvaluationEligibilityService {
    private final ProfessorRepository professors;
    private final EvaluationPolicyRepository policies;
    private final SemesterRepository semesters;
    private final EvaluationCycleRepository cycles;
    private final ObserveeRepository observees;
    private final EvaluationSignupRepository signups;
    private final ObservationRepository observations;
    private final EvaluationNotificationRepository notifications;
    private final NotificationEmailSender emailSender;
    private final Clock clock;
    private final edu.utdallas.cs4485.assessmenthelper.repository.EvaluationDeferralRepository deferrals;
    private final EmailTemplateService emailTemplates;

    public EvaluationEligibilityService(
            ProfessorRepository professors,
            EvaluationPolicyRepository policies,
            SemesterRepository semesters,
            EvaluationCycleRepository cycles,
            ObserveeRepository observees,
            EvaluationSignupRepository signups,
            ObservationRepository observations,
            EvaluationNotificationRepository notifications,
            NotificationEmailSender emailSender,
            Clock clock,
            edu.utdallas.cs4485.assessmenthelper.repository.EvaluationDeferralRepository deferrals,
            EmailTemplateService emailTemplates) {
        this.professors = professors;
        this.policies = policies;
        this.semesters = semesters;
        this.cycles = cycles;
        this.observees = observees;
        this.signups = signups;
        this.observations = observations;
        this.notifications = notifications;
        this.emailSender = emailSender;
        this.clock = clock;
        this.deferrals = deferrals;
        this.emailTemplates = emailTemplates;
    }

    public record Faculty(
            Integer professorId,
            String name,
            String lastName,
            String rank,
            String email,
            String lastEvaluationCycle,
            LocalDate nextDueDate,
            String nextDueSemester,
            int intervalYears,
            LocalDateTime lastNotifiedAt,
            String status) {}

    public record Queue(Integer cycleId, String cycleLabel, int count, List<Faculty> faculty) {}

    public record Email(String recipient, String subject, String body) {}

    @Transactional(readOnly = true)
    public Queue findOverdue() {
        return findQueue(false);
    }

    @Transactional(readOnly = true)
    public Queue findOverdue(int cycleId) {
        return findQueue(false, cycleId);
    }

    @Transactional(readOnly = true)
    public Queue findDue() {
        return findQueue(true);
    }

    @Transactional(readOnly = true)
    public Queue findDue(int cycleId) {
        return findQueue(true, cycleId);
    }

    @Transactional(readOnly = true)
    public Queue findAll() {
        return findQueue(null);
    }

    @Transactional(readOnly = true)
    public Queue findAll(int cycleId) {
        return findQueue(null, cycleId);
    }

    /** Both queues use one calculation so due always takes precedence over overdue. */
    private Queue findQueue(Boolean due) {
        return findQueue(due, null);
    }

    private Queue findQueue(Boolean due, Integer selectedCycleId) {
        LocalDate today = LocalDate.now(clock);
        var futureOnly = java.util.Set.copyOf(cycles.findFutureOnlyProfessorIds(today));
        List<Professor> activeProfessors = professors.findAll().stream()
                .filter(professor -> Boolean.TRUE.equals(professor.activeStatus()))
                .filter(professor -> !futureOnly.contains(professor.professorId()))
                .toList();
        var activeIds = activeProfessors.stream().map(Professor::professorId).collect(Collectors.toSet());
        var semesterMap =
                semesters.findAll().stream().collect(Collectors.toMap(Semester::semesterId, Function.identity()));
        var cycleMap =
                cycles.findAll().stream().collect(Collectors.toMap(EvaluationCycle::cycleId, Function.identity()));
        var current = semesterMap.values().stream()
                .filter(s -> s.startDate() != null
                        && s.endDate() != null
                        && !today.isBefore(s.startDate())
                        && !today.isAfter(s.endDate()))
                .toList();
        EvaluationCycle cycle;
        Semester semester;
        if (selectedCycleId != null) {
            cycle = cycleMap.get(selectedCycleId);
            if (cycle == null) throw new NotFoundException("Evaluation cycle not found.");
            semester = semesterMap.get(cycle.semesterId());
            if (semester == null || semester.startDate() == null || semester.endDate() == null)
                throw conflict("The cycle's semester dates are not configured.");
        } else {
            if (current.isEmpty()) throw conflict("There is no current evaluation cycle.");
            if (current.size() != 1)
                throw conflict("No unique semester contains today's date. Configure semester dates first.");
            semester = current.getFirst();
            var currentCycles = cycleMap.values().stream()
                    .filter(c -> c.semesterId().equals(semester.semesterId()))
                    .toList();
            if (currentCycles.isEmpty()) throw conflict("There is no current evaluation cycle.");
            if (currentCycles.size() != 1)
                throw conflict("Configure exactly one evaluation cycle for the current semester.");
            cycle = currentCycles.getFirst();
        }
        var roster = cycles.findRosterProfessors(cycle.cycleId());
        LocalDate cutoff =
                roster.isEmpty() ? semester.endDate() : semester.startDate().minusDays(1);
        LocalDate asOf = today.isAfter(cutoff) ? cutoff : today;
        if (!roster.isEmpty()) {
            activeProfessors = roster.stream()
                    .filter(professor -> Boolean.TRUE.equals(professor.activeStatus()))
                    .toList();
            activeIds = activeProfessors.stream().map(Professor::professorId).collect(Collectors.toSet());
        }
        var policyMap = policies.findAll().stream()
                .collect(Collectors.toMap(EvaluationPolicy::professorRank, Function.identity()));
        var signupMap =
                signups.findAll().stream().collect(Collectors.toMap(EvaluationSignup::signupId, Function.identity()));
        Map<Integer, LocalDateTime> completionByObservee = new HashMap<>();
        var completedAfterAsOf = new java.util.HashSet<Integer>();
        for (Observation observation : observations.findAll()) {
            var signup = signupMap.get(observation.signupId());
            if (signup != null
                    && observation.completedAt() != null
                    && observation.completedAt().toLocalDate().isAfter(asOf))
                completedAfterAsOf.add(signup.observeeId());
            if (signup != null
                    && observation.completedAt() != null
                    && !observation.completedAt().toLocalDate().isAfter(asOf)) {
                completionByObservee.merge(
                        signup.observeeId(), observation.completedAt(), (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        Map<Integer, LocalDate> lastCompleted = new HashMap<>();
        Map<Integer, String> lastCycle = new HashMap<>();
        var allObservees = observees.findAll();
        for (Observee observee : allObservees) {
            if (!activeIds.contains(observee.professorId()) || !"COMPLETED".equalsIgnoreCase(observee.status()))
                continue;
            LocalDateTime completedAt = completionByObservee.get(observee.observeeId());
            if (completedAt == null && completedAfterAsOf.contains(observee.observeeId())) continue;
            if (completedAt == null)
                throw conflict("Completed evaluation for professor " + observee.professorId()
                        + " is missing its observation completion date.");
            LocalDate date = completedAt.toLocalDate();
            if (!lastCompleted.containsKey(observee.professorId())
                    || date.isAfter(lastCompleted.get(observee.professorId()))) {
                lastCompleted.put(observee.professorId(), date);
                var pastCycle = cycleMap.get(observee.cycleId());
                var pastSemester = pastCycle == null ? null : semesterMap.get(pastCycle.semesterId());
                lastCycle.put(observee.professorId(), pastSemester == null ? date.toString() : label(pastSemester));
            }
        }
        Map<Integer, LocalDate> lastIncomplete = new HashMap<>();
        if (!roster.isEmpty()) {
            for (Observee observee : allObservees) {
                if (!activeIds.contains(observee.professorId())
                        || "DEFERRED".equalsIgnoreCase(observee.status())
                        || "CANCELLED".equalsIgnoreCase(observee.status())) continue;
                var earlierCycle = cycleMap.get(observee.cycleId());
                var earlierSemester = earlierCycle == null ? null : semesterMap.get(earlierCycle.semesterId());
                if (earlierSemester == null
                        || earlierSemester.endDate() == null
                        || !earlierSemester.endDate().isBefore(semester.startDate())) continue;
                var completed = completionByObservee.get(observee.observeeId());
                if ("COMPLETED".equalsIgnoreCase(observee.status())
                        && completed != null
                        && !completed.toLocalDate().isAfter(earlierSemester.endDate())) continue;
                lastIncomplete.merge(observee.professorId(), earlierSemester.endDate(), (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        Map<Integer, LocalDateTime> notifiedAt = new HashMap<>();
        for (var notification : notifications.findAll()) {
            if (cycle.cycleId().equals(notification.cycleId()))
                notifiedAt.merge(notification.professorId(), notification.sentAt(), (a, b) -> a.isAfter(b) ? a : b);
        }
        var deferred = deferrals.findAll();
        List<Faculty> faculty = new ArrayList<>();
        for (Professor professor : activeProfessors) {
            var policy = policyMap.get(professor.professorRank());
            LocalDate anchor = lastCompleted.getOrDefault(professor.professorId(), professor.hireDate());
            if (!roster.isEmpty() && (policy == null || anchor == null)) {
                if (due == null)
                    faculty.add(new Faculty(
                            professor.professorId(),
                            professor.firstName() + " " + professor.lastName(),
                            professor.lastName(),
                            professor.professorRank(),
                            professor.email(),
                            lastCycle.getOrDefault(professor.professorId(), "Never evaluated"),
                            null,
                            null,
                            0,
                            notifiedAt.get(professor.professorId()),
                            "Unconfigured"));
                continue;
            }
            if (policy == null)
                throw conflict("Professor " + professor.professorId() + " needs an evaluation policy for their rank.");
            if (!EvaluationDueRules.applies(professor, policy)) continue;
            Integer interval = policy.evaluationIntervalYears();
            if (interval == null || interval < 1) throw conflict("Evaluation intervals must be positive.");
            if (anchor == null)
                throw conflict(
                        "Professor " + professor.professorId() + " needs a hire date or completed evaluation date.");
            var result = EvaluationDueRules.calculate(anchor, interval, semester);
            LocalDate last = lastCompleted.get(professor.professorId());
            LocalDate target = deferred.stream()
                    .filter(d -> d.professorId().equals(professor.professorId())
                            && !d.deferredAt().toLocalDate().isAfter(asOf)
                            && (last == null || last.isBefore(d.deferredAt().toLocalDate())))
                    .map(d -> semesterMap.get(d.targetSemesterId()))
                    .filter(java.util.Objects::nonNull)
                    .map(Semester::startDate)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            result = EvaluationDueRules.deferred(result, target, semester);
            // A roster-backed cycle keeps an outstanding obligation overdue, even when a later
            // recurrence would otherwise fall in this semester. An explicit deferral still applies.
            if (!roster.isEmpty() && target == null && result.firstDue().isBefore(semester.startDate())) {
                result = new EvaluationDueRules.Result(result.firstDue(), result.firstDue(), false, true);
            }
            LocalDate incomplete = lastIncomplete.get(professor.professorId());
            if (target == null && incomplete != null && (last == null || last.isBefore(incomplete))) {
                LocalDate overdueDate = result.firstDue().isBefore(incomplete) ? result.firstDue() : incomplete;
                result = new EvaluationDueRules.Result(result.firstDue(), overdueDate, false, true);
            }
            boolean dueThisCycle = result.dueThisCycle();
            boolean overdue = result.overdue();
            if (due == null || (due ? dueThisCycle : overdue)) {
                LocalDate dueDate = result.nextDue();
                String dueSemester = semesterMap.values().stream()
                        .filter(s -> s.startDate() != null
                                && s.endDate() != null
                                && !dueDate.isBefore(s.startDate())
                                && !dueDate.isAfter(s.endDate()))
                        .sorted(Comparator.comparing(Semester::startDate).thenComparing(Semester::semesterId))
                        .map(EvaluationEligibilityService::label)
                        .findFirst()
                        .orElse(dueDate.toString());
                faculty.add(new Faculty(
                        professor.professorId(),
                        professor.firstName() + " " + professor.lastName(),
                        professor.lastName(),
                        professor.professorRank(),
                        professor.email(),
                        lastCycle.getOrDefault(professor.professorId(), "Never evaluated"),
                        dueDate,
                        dueSemester,
                        interval,
                        notifiedAt.get(professor.professorId()),
                        target != null && target.isAfter(semester.endDate())
                                ? "Due next semester"
                                : dueThisCycle ? "Due" : overdue ? "Overdue" : "Upcoming"));
            }
        }
        faculty.sort(Comparator.comparing(Faculty::name).thenComparing(Faculty::professorId));
        return new Queue(cycle.cycleId(), label(semester), faculty.size(), List.copyOf(faculty));
    }

    @Transactional
    public EvaluationNotification notifyProfessor(Integer cycleId, Integer professorId, Email email) {
        return notifyProfessor(cycleId, professorId, email, false);
    }

    @Transactional
    public EvaluationNotification notifyDueProfessor(Integer cycleId, Integer professorId, Email email) {
        return notifyProfessor(cycleId, professorId, email, true);
    }

    private EvaluationNotification notifyProfessor(Integer cycleId, Integer professorId, Email email, boolean due) {
        Queue queue = findQueue(due);
        if (!queue.cycleId().equals(cycleId))
            throw conflict("The evaluation cycle changed. Refresh the evaluation list.");
        Faculty faculty = queue.faculty().stream()
                .filter(p -> p.professorId().equals(professorId))
                .findFirst()
                .orElseThrow(() ->
                        conflict("This professor is no longer " + (due ? "due" : "overdue") + ". Refresh the list."));
        if (email == null
                || !faculty.email().equals(email.recipient())
                || email.subject() == null
                || email.subject().isBlank()
                || email.body() == null
                || email.body().isBlank()
                || email.subject().length() > 255
                || email.body().length() > 16000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Provide the professor's email, a subject (up to 255 characters), and a message (up to 16000 characters).");
        }
        emailSender.send(email.recipient(), email.subject(), email.body());
        return notifications.save(new EvaluationNotification(
                null, professorId, cycleId, faculty.email(), email.subject(), email.body(), LocalDateTime.now(clock)));
    }

    /** Builds a draft from server-owned faculty data and the current saved template. */
    @Transactional(readOnly = true)
    public Email previewNotification(Integer cycleId, Integer professorId, boolean due) {
        Queue queue = findQueue(due);
        if (!queue.cycleId().equals(cycleId))
            throw conflict("The evaluation cycle changed. Refresh the evaluation list.");
        Faculty faculty = queue.faculty().stream()
                .filter(p -> p.professorId().equals(professorId))
                .findFirst()
                .orElseThrow(() ->
                        conflict("This professor is no longer " + (due ? "due" : "overdue") + ". Refresh the list."));
        String type =
                "EVALUATION_" + (due ? "DUE" : "OVERDUE") + (faculty.lastNotifiedAt() != null ? "_FOLLOW_UP" : "");
        var cycle = cycles.findById(cycleId).orElseThrow(() -> new NotFoundException("Evaluation cycle not found."));
        var context = new HashMap<String, String>();
        context.put("professorName", faculty.name());
        context.put("lastName", faculty.lastName());
        context.put("cycleLabel", queue.cycleLabel());
        context.put(
                "rankDescription",
                (faculty.rank().equals("FULL") ? "a " : "an ") + faculty.rank().charAt(0)
                        + faculty.rank().substring(1).toLowerCase(java.util.Locale.ROOT) + " Professor");
        context.put("interval", faculty.intervalYears() == 1 ? "year" : faculty.intervalYears() + " years");
        context.put(
                "evaluationHistory",
                faculty.lastEvaluationCycle().equals("Never evaluated")
                        ? "Our records do not show a completed evaluation for you."
                        : "Your last evaluation was completed in " + faculty.lastEvaluationCycle() + ".");
        if (cycle.signupDeadline() != null)
            context.put("signupDeadline", cycle.signupDeadline().toString());
        if (cycle.observationDeadline() != null)
            context.put("observationDeadline", cycle.observationDeadline().toString());
        var preview = emailTemplates.render(type, context);
        return new Email(faculty.email(), preview.subject(), preview.body());
    }

    private static String label(Semester semester) {
        return semester.term() + " " + semester.academicYear();
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
