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
public class OverdueEvaluationService {
    private final ProfessorRepository professors;
    private final EvaluationPolicyRepository policies;
    private final SemesterRepository semesters;
    private final EvaluationCycleRepository cycles;
    private final ObserveeRepository observees;
    private final EvaluationSignupRepository signups;
    private final ObservationRepository observations;
    private final EvaluationNotificationRepository notifications;
    private final Clock clock;

    public OverdueEvaluationService(
            ProfessorRepository professors,
            EvaluationPolicyRepository policies,
            SemesterRepository semesters,
            EvaluationCycleRepository cycles,
            ObserveeRepository observees,
            EvaluationSignupRepository signups,
            ObservationRepository observations,
            EvaluationNotificationRepository notifications,
            Clock clock) {
        this.professors = professors;
        this.policies = policies;
        this.semesters = semesters;
        this.cycles = cycles;
        this.observees = observees;
        this.signups = signups;
        this.observations = observations;
        this.notifications = notifications;
        this.clock = clock;
    }

    public record Faculty(Integer professorId, String name, String rank, String email,
            String lastEvaluationCycle, LocalDate nextDueDate, int intervalYears,
            LocalDateTime lastNotifiedAt) {}
    public record Queue(Integer cycleId, String cycleLabel, int count, List<Faculty> faculty) {}
    public record Email(String recipient, String subject, String body) {}

    @Transactional(readOnly = true)
    public Queue findOverdue() {
        LocalDate today = LocalDate.now(clock);
        List<Professor> activeProfessors = professors.findAll().stream()
                .filter(professor -> Boolean.TRUE.equals(professor.activeStatus()))
                .toList();
        var activeIds = activeProfessors.stream().map(Professor::professorId).collect(Collectors.toSet());
        var semesterMap = semesters.findAll().stream().collect(Collectors.toMap(Semester::semesterId, Function.identity()));
        var current = semesterMap.values().stream().filter(s -> s.startDate() != null && s.endDate() != null
                && !today.isBefore(s.startDate()) && !today.isAfter(s.endDate())).toList();
        if (current.size() != 1) throw conflict("No unique semester contains today's date. Configure semester dates first.");
        Semester semester = current.getFirst();
        var cycleMap = cycles.findAll().stream().collect(Collectors.toMap(EvaluationCycle::cycleId, Function.identity()));
        var currentCycles = cycleMap.values().stream().filter(c -> c.semesterId().equals(semester.semesterId())).toList();
        if (currentCycles.size() != 1) throw conflict("Configure exactly one evaluation cycle for the current semester.");
        EvaluationCycle cycle = currentCycles.getFirst();
        var policyMap = policies.findAll().stream().collect(Collectors.toMap(EvaluationPolicy::professorRank, Function.identity()));
        var signupMap = signups.findAll().stream().collect(Collectors.toMap(EvaluationSignup::signupId, Function.identity()));
        Map<Integer, LocalDateTime> completionByObservee = new HashMap<>();
        for (Observation observation : observations.findAll()) {
            var signup = signupMap.get(observation.signupId());
            if (signup != null && observation.completedAt() != null && !observation.completedAt().toLocalDate().isAfter(today)) {
                completionByObservee.merge(signup.observeeId(), observation.completedAt(), (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        Map<Integer, LocalDate> lastCompleted = new HashMap<>();
        Map<Integer, String> lastCycle = new HashMap<>();
        for (Observee observee : observees.findAll()) {
            if (!activeIds.contains(observee.professorId()) || !"COMPLETED".equalsIgnoreCase(observee.status())) continue;
            LocalDateTime completedAt = completionByObservee.get(observee.observeeId());
            if (completedAt == null) throw conflict("Completed evaluation for professor " + observee.professorId() + " is missing its observation completion date.");
            LocalDate date = completedAt.toLocalDate();
            if (!lastCompleted.containsKey(observee.professorId()) || date.isAfter(lastCompleted.get(observee.professorId()))) {
                lastCompleted.put(observee.professorId(), date);
                var pastCycle = cycleMap.get(observee.cycleId());
                var pastSemester = pastCycle == null ? null : semesterMap.get(pastCycle.semesterId());
                lastCycle.put(observee.professorId(), pastSemester == null ? date.toString() : label(pastSemester));
            }
        }
        Map<Integer, LocalDateTime> notifiedAt = new HashMap<>();
        for (var notification : notifications.findAll()) {
            if (cycle.cycleId().equals(notification.cycleId())) notifiedAt.merge(notification.professorId(), notification.sentAt(),
                    (a, b) -> a.isAfter(b) ? a : b);
        }
        List<Faculty> faculty = new ArrayList<>();
        for (Professor professor : activeProfessors) {
            var policy = policyMap.get(professor.professorRank());
            if (policy == null) throw conflict("Professor " + professor.professorId() + " needs an evaluation policy for their rank.");
            if (!Boolean.TRUE.equals(policy.activeStatus())) continue;
            Integer interval = policy.evaluationIntervalYears();
            if (interval == null || interval < 1) throw conflict("Evaluation intervals must be positive.");
            LocalDate anchor = lastCompleted.getOrDefault(professor.professorId(), professor.hireDate());
            if (anchor == null) throw conflict("Professor " + professor.professorId() + " needs a hire date or completed evaluation date.");
            LocalDate firstDue = anchor.plusYears(interval);
            // Retain the missed obligation, but current-cycle recurrence takes priority over overdue.
            LocalDate recurrence = firstDue;
            int years = interval;
            while (recurrence.isBefore(semester.startDate())) {
                years += interval;
                recurrence = anchor.plusYears(years);
            }
            boolean dueThisCycle = !recurrence.isAfter(semester.endDate());
            if (firstDue.isBefore(semester.startDate()) && !dueThisCycle) {
                faculty.add(new Faculty(professor.professorId(), professor.firstName() + " " + professor.lastName(),
                        professor.professorRank(), professor.email(), lastCycle.getOrDefault(professor.professorId(), "Never evaluated"),
                        firstDue, interval, notifiedAt.get(professor.professorId())));
            }
        }
        faculty.sort(Comparator.comparing(Faculty::name).thenComparing(Faculty::professorId));
        return new Queue(cycle.cycleId(), label(semester), faculty.size(), List.copyOf(faculty));
    }

    @Transactional
    public EvaluationNotification notifyProfessor(Integer cycleId, Integer professorId, Email email) {
        Queue queue = findOverdue();
        if (!queue.cycleId().equals(cycleId)) throw conflict("The evaluation cycle changed. Refresh the overdue list.");
        Faculty faculty = queue.faculty().stream().filter(p -> p.professorId().equals(professorId)).findFirst()
                .orElseThrow(() -> conflict("This professor is no longer overdue. Refresh the list."));
        if (email == null || !faculty.email().equals(email.recipient()) || email.subject() == null || email.subject().isBlank()
                || email.body() == null || email.body().isBlank() || email.subject().length() > 255 || email.body().length() > 16000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide the professor's email, a subject (up to 255 characters), and a message (up to 16000 characters).");
        }
        return notifications.save(new EvaluationNotification(null, professorId, cycleId, faculty.email(),
                email.subject(), email.body(), LocalDateTime.now(clock)));
    }

    private static String label(Semester semester) { return semester.term() + " " + semester.academicYear(); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
