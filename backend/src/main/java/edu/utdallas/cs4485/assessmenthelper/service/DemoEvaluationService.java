package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.entity.Observation;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverRequest;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationSignupRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationTemplateRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRequestRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.UserAccountRepository;
import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnBooleanProperty("app.demo.enabled")
@Transactional
public class DemoEvaluationService {
    private final MissingObservationService missing;
    private final ObserverEligibilityService eligibility;
    private final ObservationService observationService;
    private final ObservationTemplateService templateService;
    private final DemoService demo;
    private final EvaluationSignupRepository signups;
    private final ObserverRepository observers;
    private final ObserverRequestRepository requests;
    private final ObservationRepository observations;
    private final ObservationTemplateRepository templates;
    private final UserAccountRepository accounts;
    private final Clock clock;

    public DemoEvaluationService(
            MissingObservationService missing,
            ObserverEligibilityService eligibility,
            ObservationService observationService,
            ObservationTemplateService templateService,
            DemoService demo,
            EvaluationSignupRepository signups,
            ObserverRepository observers,
            ObserverRequestRepository requests,
            ObservationRepository observations,
            ObservationTemplateRepository templates,
            UserAccountRepository accounts,
            Clock clock) {
        this.missing = missing;
        this.eligibility = eligibility;
        this.observationService = observationService;
        this.templateService = templateService;
        this.demo = demo;
        this.signups = signups;
        this.observers = observers;
        this.requests = requests;
        this.observations = observations;
        this.templates = templates;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MissingObservationService.MissingSignup> open() {
        return missing.withoutCompletedObservation(eligibility.cycle().cycleId());
    }

    public record Step(int observationId, String status) {}

    /**
     * Moves the signup's observation one step along the path a real one takes, acting as its observer or observee, so
     * the audit log gets the usual entries. A signup without an observation gets an observer first, skipping the
     * deadline and invitation checks that a committee assignment makes.
     */
    public Step step(int signupId) {
        if (open().stream().noneMatch(signup -> signup.signupId() == signupId))
            throw new ConflictException("This signup is not waiting on an observation this semester.");
        var owner = signups.findOwner(signupId).orElseThrow(() -> new NotFoundException("Signup not found."));
        var current = observations.findBySignupIdIn(List.of(signupId)).stream()
                .max(Comparator.comparing((Observation o) -> "SUBMITTED".equals(o.status()))
                        .thenComparing(Observation::observationId));
        if (current.isEmpty()) {
            Observation assigned = assignObserver(signupId);
            return new Step(assigned.observationId(), assigned.status());
        }
        Observation observation = current.get();
        int id = observation.observationId();
        AccountPrincipal observer = principalOf(
                observers.findById(observation.observerId()).orElseThrow().professorId());
        ObservationService.Detail detail = switch (observation.status()) {
            case "ACCEPTED", "SCHEDULED" -> observationService.start(id, observer);
            case "IN_PROGRESS" -> observationService.submit(id, answers(observation.templateId()), observer);
            default -> observationService.observeeSign(id, null, principalOf(owner.professorId()));
        };
        return new Step(id, detail.status().name());
    }

    public int complete(int signupId) {
        Step step;
        do step = step(signupId);
        while (!"COMPLETED".equals(step.status()));
        return step.observationId();
    }

    private Observation assignObserver(int signupId) {
        LocalDateTime now = LocalDateTime.now(clock);
        var pending = requests.findBySignupId(signupId).stream()
                .filter(r -> "PENDING".equals(r.status())
                        && (r.expirationDate() == null || r.expirationDate().isAfter(now)))
                .findFirst();
        int observerId;
        if (pending.isPresent()) {
            requests.save(pending.get().withStatus("CONFIRMED", now));
            observerId = pending.get().observerId();
        } else {
            List<Integer> eligible = eligibility.eligibleObserverIdsInWholeWindow(signupId);
            if (eligible.isEmpty()) throw new ConflictException("No professor is eligible to observe this signup.");
            observerId = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
            requests.save(new ObserverRequest(
                    null,
                    signupId,
                    observerId,
                    now,
                    now.plus(ObserverRequestService.DEFAULT_RESPONSE_WINDOW),
                    now,
                    "CONFIRMED",
                    true));
        }
        int templateId = templates
                .findFirstByActiveStatusTrueOrderByVersionDescTemplateIdDesc()
                .orElseThrow(() -> new ConflictException("Configure an active observation template first."))
                .templateId();
        return observations.save(new Observation(
                null, signupId, observerId, templateId, null, null, "ACCEPTED", false, false, null, null, null, null));
    }

    private List<ObservationService.Answer> answers(int templateId) {
        return templateService.template(templateId).criteria().stream()
                .map(q -> "RATING".equals(q.questionType())
                        ? new ObservationService.Answer(
                                q.criterionId(), Objects.requireNonNullElse(q.ratingMax(), 5), null)
                        : new ObservationService.Answer(q.criterionId(), null, "Recorded with the demo tool."))
                .toList();
    }

    private AccountPrincipal principalOf(int professorId) {
        return accounts.findByProfessorId(professorId)
                .map(account -> demo.principal(account.userId()))
                .orElseThrow(() -> new ConflictException("A professor on this evaluation has no account."));
    }
}
