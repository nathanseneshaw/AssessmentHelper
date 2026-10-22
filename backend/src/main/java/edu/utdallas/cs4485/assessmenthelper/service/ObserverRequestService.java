package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.entity.ObserverRequest;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationSignupRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRequestRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lifecycle of a request asking an observer to observe a signup: PENDING, then CONFIRMED when the observer accepts
 * before the expiration date, or EXPIRED when the expiration date passes without a response.
 *
 * <p>Expiry is applied two ways. Every read and every response first expires the request if it is overdue, so callers
 * never see a stale PENDING, and a scheduled sweep expires the rest so the stored status stays accurate.
 */
@Service
public class ObserverRequestService {

    public static final String PENDING = "PENDING";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String EXPIRED = "EXPIRED";

    /** How long an observer has to respond when the caller does not pick an expiration date. */
    static final Duration DEFAULT_RESPONSE_WINDOW = Duration.ofDays(7);

    private final ObserverRequestRepository repository;
    private final EvaluationSignupRepository signupRepository;
    private final ObserverRepository observerRepository;
    private final Clock clock;
    private ObserverEligibilityService eligibility;

    @Autowired(required = false)
    void setEligibility(ObserverEligibilityService eligibility) {
        this.eligibility = eligibility;
    }

    public ObserverRequestService(
            ObserverRequestRepository repository,
            EvaluationSignupRepository signupRepository,
            ObserverRepository observerRepository,
            Clock clock) {
        this.repository = repository;
        this.signupRepository = signupRepository;
        this.observerRepository = observerRepository;
        this.clock = clock;
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
        if (!signupRepository.existsById(signupId)) {
            throw new NotFoundException("No evaluation signup with id " + signupId);
        }
        if (!observerRepository.existsById(observerId)) {
            throw new NotFoundException("No observer with id " + observerId);
        }
        if (eligibility != null && eligibility.manages(signupId)) {
            eligibility.sendExistingRequest(signupId, observerId);
            return repository.findBySignupId(signupId).stream()
                    .filter(r -> r.observerId().equals(observerId))
                    .findFirst()
                    .orElseThrow();
        }
        LocalDateTime now = now();
        LocalDateTime expires = expirationDate != null ? expirationDate : now.plus(DEFAULT_RESPONSE_WINDOW);
        if (!expires.isAfter(now)) {
            throw new IllegalArgumentException("expirationDate must be in the future");
        }
        return repository.save(new ObserverRequest(null, signupId, observerId, now, expires, null, PENDING));
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
        return requests.stream().map(this::expireIfOverdue).toList();
    }

    /**
     * The observer accepts the request.
     *
     * @throws NotFoundException if there is no such request
     * @throws ConflictException if the request has expired or was already confirmed
     */
    @Transactional
    public ObserverRequest confirm(Integer requestId) {
        ObserverRequest request = expireIfOverdue(load(requestId));
        if (eligibility != null && eligibility.manages(request.signupId())) {
            eligibility.confirmExistingRequest(requestId);
            return load(requestId);
        }
        if (EXPIRED.equals(request.status())) {
            throw new ConflictException("Request " + requestId + " has expired");
        }
        if (!PENDING.equals(request.status())) {
            throw new ConflictException("Request " + requestId + " is already " + request.status());
        }
        return repository.save(withStatus(request, CONFIRMED, now()));
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
