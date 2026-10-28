package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.entity.ObserverRequest;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationSignupRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverMatchRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRequestContextRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRequestContextRepository.Candidate;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRequestContextRepository.SignupContext;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRequestRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lifecycle of a request asking an observer to observe a signup: PENDING, then CONFIRMED when the observer accepts
 * before the expiration date, or EXPIRED when the expiration date passes without a response.
 *
 * <p>Expiry is applied two ways. Every read and every response first expires the request if it is overdue, so callers
 * never see a stale PENDING, and a scheduled sweep expires the rest so the stored status stays accurate.
 *
 * <p>Signups with a generated observer list also follow {@link ObserverEligibilityService}: requests wait for the
 * committee to release the list, the observer must still be eligible, and the observer can decline (DECLINED).
 */
@Service
public class ObserverRequestService {

    public static final String PENDING = "PENDING";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String EXPIRED = "EXPIRED";
    public static final String DECLINED = "DECLINED";

    /** How long an observer has to respond when the caller does not pick an expiration date. */
    static final Duration DEFAULT_RESPONSE_WINDOW = Duration.ofDays(7);

    private final ObserverRequestRepository repository;
    private final EvaluationSignupRepository signupRepository;
    private final ObserverRepository observerRepository;
    private final Clock clock;
    private final ObserverMatchRepository matches;
    private final ObserverRequestContextRepository context;
    private final ProfessorRepository professors;
    private final ObserverEligibilityService eligibility;

    public ObserverRequestService(
            ObserverRequestRepository repository,
            EvaluationSignupRepository signupRepository,
            ObserverRepository observerRepository,
            Clock clock,
            ObserverMatchRepository matches,
            ObserverRequestContextRepository context,
            ProfessorRepository professors,
            ObserverEligibilityService eligibility) {
        this.repository = repository;
        this.signupRepository = signupRepository;
        this.observerRepository = observerRepository;
        this.clock = clock;
        this.matches = matches;
        this.context = context;
        this.professors = professors;
        this.eligibility = eligibility;
    }

    /**
     * Creates a PENDING request.
     *
     * @param expirationDate when the request lapses, or null for {@link #DEFAULT_RESPONSE_WINDOW} from now
     * @throws IllegalArgumentException if an id is null or the expiration date is not in the future
     * @throws NotFoundException if the signup or observer does not exist
     */
    @Transactional
    public ObserverRequest create(Integer signupId, Integer observerId, LocalDateTime expirationDate) {
        if (signupId == null || observerId == null) {
            throw new IllegalArgumentException("signupId and observerId are required");
        }
        var signup = lockSignup(signupId);
        if (!observerRepository.existsById(observerId)) {
            throw new NotFoundException("No observer with id " + observerId);
        }
        String reason = unavailableReason(signup);
        if (reason != null) {
            throw new ConflictException(reason);
        }
        if (context.candidates(signupId).stream()
                .noneMatch(candidate -> candidate.observerId().equals(observerId))) {
            throw new ConflictException("Choose an active proposed observer from this signup's evaluation cycle.");
        }
        LocalDateTime now = now();
        LocalDateTime expires = expirationDate != null ? expirationDate : now.plus(DEFAULT_RESPONSE_WINDOW);
        if (!expires.isAfter(now)) {
            throw new IllegalArgumentException("expirationDate must be in the future");
        }
        // The professor/signup locks serialize retries and requests to different candidates.
        var active = find(signupId, null).stream()
                .filter(request -> PENDING.equals(request.status()) || CONFIRMED.equals(request.status()))
                .toList();
        var existing = active.stream()
                .filter(request -> request.observerId().equals(observerId))
                .findFirst();
        if (existing.isPresent()) return existing.get();
        if (active.stream().anyMatch(request -> CONFIRMED.equals(request.status()))) {
            throw new ConflictException("This signup already has a confirmed observer request.");
        }
        if (active.size() >= 5) {
            throw new ConflictException("You can have up to 5 pending observer requests per signup.");
        }
        eligibility.requireEligible(signupId, observerId);
        var saved = repository.save(new ObserverRequest(null, signupId, observerId, now, expires, null, PENDING));
        eligibility.requestSent(signupId, observerId);
        return saved;
    }

    public record RequestSummary(ObserverRequest request, String observerName) {}

    public record RequestOptions(
            Integer signupId,
            String courseLabel,
            String semesterLabel,
            String signupStatus,
            String unavailableReason,
            List<Candidate> candidates,
            List<RequestSummary> requests) {}

    @Transactional
    public List<RequestOptions> options(Integer professorId) {
        if (!professors.existsById(professorId)) {
            throw new NotFoundException("No professor with id " + professorId);
        }
        return context.findSignups(professorId).stream()
                .map(signup -> new RequestOptions(
                        signup.signupId(),
                        signup.courseLabel(),
                        signup.semesterLabel(),
                        signup.signupStatus(),
                        unavailableReason(signup),
                        eligibility.candidatesVisible(signup.signupId())
                                ? context.candidates(signup.signupId())
                                : List.of(),
                        find(signup.signupId(), null).stream()
                                .map(request -> new RequestSummary(request, context.observerName(request.observerId())))
                                .toList()))
                .toList();
    }

    @Transactional
    public ObserverRequest createFromMatch(
            Integer professorId, Integer signupId, Integer matchId, LocalDateTime expirationDate) {
        if (signupId == null || matchId == null) {
            throw new IllegalArgumentException("signupId and matchId are required");
        }
        var signup = context.findSignup(signupId)
                .orElseThrow(() -> new NotFoundException("No evaluation signup with id " + signupId));
        if (!signup.professorId().equals(professorId)) {
            throw new NotFoundException("No evaluation signup for this professor with id " + signupId);
        }
        var match = matches.findById(matchId)
                .orElseThrow(() -> new NotFoundException("No observer match with id " + matchId));
        if (!match.signupId().equals(signupId)) {
            throw new IllegalArgumentException("The proposed match does not belong to this signup.");
        }
        if (!"PROPOSED".equals(match.matchStatus())) {
            throw new ConflictException("This match is no longer proposed. Refresh the candidate list.");
        }
        return create(signupId, match.observerId(), expirationDate);
    }

    private SignupContext lockSignup(Integer signupId) {
        var initial = context.findSignup(signupId)
                .orElseThrow(() -> new NotFoundException("No evaluation signup with id " + signupId));
        // Same lock order as course selection, so its section cannot change during a send.
        professors.findForSignupUpdate(initial.professorId()).orElseThrow();
        signupRepository.findForRequestUpdate(signupId).orElseThrow();
        return context.findSignup(signupId).orElseThrow();
    }

    private String unavailableReason(SignupContext signup) {
        if (!Boolean.TRUE.equals(signup.professorActive())) {
            return "This professor is not active.";
        }
        if (!"OPEN".equals(signup.cycleStatus())) {
            return "This evaluation cycle is closed.";
        }
        if (!"ACTIVE".equals(signup.observeeStatus())
                || !("PENDING".equals(signup.signupStatus()) || "MATCHED".equals(signup.signupStatus()))) {
            return "This signup is no longer accepting observer requests.";
        }
        if (Boolean.TRUE.equals(signup.observationExists())) {
            return "An observation is already scheduled for this signup.";
        }
        return eligibility.requestBlocker(signup.signupId());
    }

    /** @throws NotFoundException if there is no such request */
    @Transactional
    public ObserverRequest findById(Integer requestId) {
        return expireIfOverdue(load(requestId));
    }

    /** Requests for one observer, or for one signup, or all of them if both are null. Signup wins if both are given. */
    @Transactional
    public List<ObserverRequest> find(Integer signupId, Integer observerId) {
        List<ObserverRequest> requests = signupId != null
                ? repository.findBySignupId(signupId)
                : observerId != null ? repository.findByObserverId(observerId) : repository.findAll();
        return requests.stream()
                .map(this::expireIfOverdue)
                .sorted(Comparator.comparing(ObserverRequest::requestId))
                .toList();
    }

    /**
     * The observer accepts the request.
     *
     * @throws NotFoundException if there is no such request
     * @throws ConflictException if the request has expired or was already confirmed
     */
    @Transactional
    public ObserverRequest confirm(Integer requestId) {
        Integer signupId = load(requestId).signupId();
        if (eligibility.manages(signupId)) {
            // Accepting a generated-list request also records the observation and notifies the observee.
            eligibility.confirmExistingRequest(requestId);
            return load(requestId);
        }
        lockSignup(signupId);
        return repository.save(withStatus(requirePending(requestId), CONFIRMED, now()));
    }
    /**
     * The observer declines the request.
     *
     * @throws NotFoundException if there is no such request
     * @throws ConflictException if the request has expired or was already answered
     */
    @Transactional
    public ObserverRequest decline(Integer requestId) {
        return repository.save(withStatus(requirePending(requestId), DECLINED, now()));
    }
    /** Marks every PENDING request past its expiration date as EXPIRED and returns how many it changed. */
    @Transactional
    public int expireOverdue() {
        List<ObserverRequest> overdue = repository.findByStatusAndExpirationDateBefore(PENDING, now());
        overdue.forEach(request -> repository.save(withStatus(request, EXPIRED, request.responseDate())));
        return overdue.size();
    }

    @Scheduled(fixedRate = 60 * 60 * 1000)
    void expireOverdueOnSchedule() {
        expireOverdue();
    }

    private ObserverRequest requirePending(Integer requestId) {
        ObserverRequest request = expireIfOverdue(load(requestId));
        if (EXPIRED.equals(request.status())) {
            throw new ConflictException("Request " + requestId + " has expired");
        }
        if (!PENDING.equals(request.status())) {
            throw new ConflictException("Request " + requestId + " is already " + request.status());
        }
        return request;
    }

    private ObserverRequest load(Integer requestId) {
        return repository
                .findById(requestId)
                .orElseThrow(() -> new NotFoundException("No observer request with id " + requestId));
    }

    private ObserverRequest expireIfOverdue(ObserverRequest request) {
        boolean overdue = PENDING.equals(request.status())
                && request.expirationDate() != null
                && request.expirationDate().isBefore(now());
        return overdue ? repository.save(withStatus(request, EXPIRED, request.responseDate())) : request;
    }

    private static ObserverRequest withStatus(ObserverRequest r, String status, LocalDateTime responseDate) {
        return new ObserverRequest(
                r.requestId(), r.signupId(), r.observerId(), r.requestDate(), r.expirationDate(), responseDate, status);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
