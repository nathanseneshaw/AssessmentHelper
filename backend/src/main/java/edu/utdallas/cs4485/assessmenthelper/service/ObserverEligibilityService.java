package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.entity.Observation;
import edu.utdallas.cs4485.assessmenthelper.entity.Observer;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverListState;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverMatch;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverNotification;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverRequest;
import edu.utdallas.cs4485.assessmenthelper.repository.CourseSectionRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationCycleRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationDeferralRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationSignupRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationTemplateRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserveeRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverListStateRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverMatchRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverNotificationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRequestRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.SemesterRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.TeachingHistoryRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persisted recommendations, committee release and assignment, observer responses and in-app notifications. Signups are saved by
 * {@link EvaluationSignupService} and requests by {@link ObserverRequestService}; both call in here for signups that
 * have an observation window, which are the ones with a generated candidate list.
 */
@Service
@Transactional
public class ObserverEligibilityService {
    /** How many candidates a generated observer list is filled up to. */
    public static final int LIST_SIZE = 5;

    private final EvaluationCycleRepository evaluationCycles;
    private final EvaluationSignupRepository evaluationSignups;
    private final EvaluationDeferralRepository deferrals;
    private final ObserveeRepository observees;
    private final ObserverRepository observers;
    private final ObserverListStateRepository listStates;
    private final ObserverMatchRepository matches;
    private final ObserverRequestRepository observerRequests;
    private final ObserverNotificationRepository observerNotifications;
    private final ObservationRepository observations;
    private final ObservationTemplateRepository templates;
    private final ProfessorRepository professors;
    private final TeachingHistoryRepository teachingHistory;
    private final CourseSectionRepository courseSections;
    private final SemesterRepository semesters;
    private final Clock clock;

    public ObserverEligibilityService(
            EvaluationCycleRepository evaluationCycles,
            EvaluationSignupRepository evaluationSignups,
            EvaluationDeferralRepository deferrals,
            ObserveeRepository observees,
            ObserverRepository observers,
            ObserverListStateRepository listStates,
            ObserverMatchRepository matches,
            ObserverRequestRepository observerRequests,
            ObserverNotificationRepository observerNotifications,
            ObservationRepository observations,
            ObservationTemplateRepository templates,
            ProfessorRepository professors,
            TeachingHistoryRepository teachingHistory,
            CourseSectionRepository courseSections,
            SemesterRepository semesters,
            Clock clock) {
        this.evaluationCycles = evaluationCycles;
        this.evaluationSignups = evaluationSignups;
        this.deferrals = deferrals;
        this.observees = observees;
        this.observers = observers;
        this.listStates = listStates;
        this.matches = matches;
        this.observerRequests = observerRequests;
        this.observerNotifications = observerNotifications;
        this.observations = observations;
        this.templates = templates;
        this.professors = professors;
        this.teachingHistory = teachingHistory;
        this.courseSections = courseSections;
        this.semesters = semesters;
        this.clock = clock;
    }

    public boolean manages(int signupId) {
        return listStates.existsById(signupId);
    }

    public void confirmExistingRequest(int requestId, LocalDate observationDate) {
        int professorId = observerRequests.findObserverProfessorId(requestId).orElseThrow();
        respond(professorId, requestId, true, observationDate);
    }

    public record Cycle(
            int cycleId,
            int semesterId,
            String label,
            LocalDate startDate,
            LocalDate signupDeadline,
            LocalDate observationDeadline,
            LocalDate feedbackDeadline,
            boolean signupOpen,
            boolean requestsOpen,
            boolean candidateListsOpen) {}

    public record Person(int professorId, String name, String department) {}

    public record Section(
            int sectionId,
            String label,
            String courseName,
            String meetingDays,
            LocalTime startTime,
            LocalTime endTime) {}

    public record Candidate(
            int observerId,
            int professorId,
            String name,
            String department,
            String level,
            List<String> qualifyingCourses) {}

    public record Signup(
            int signupId,
            int professorId,
            String name,
            String department,
            Section section,
            LocalDate windowStart,
            LocalDate windowEnd,
            boolean released,
            /* The professor recorded as observing this signup, once a request is accepted or the committee assigns one. */
            String observerName,
            List<Candidate> candidates) {}

    public record Request(
            int requestId,
            int signupId,
            int professorId,
            String observeeName,
            String observerName,
            Section section,
            LocalDate windowStart,
            LocalDate windowEnd,
            String status,
            LocalDate observationDate,
            boolean assignedByAc) {}

    public record Notification(int notificationId, String message, OffsetDateTime createdAt) {}

    public record FacultyState(
            Cycle cycle,
            Person professor,
            Signup signup,
            List<Request> incoming,
            List<Request> outgoing,
            List<Notification> notifications) {}

    public record Invitation(
            int requestId,
            int observerId,
            String observerName,
            String status,
            LocalDateTime requestedAt,
            LocalDateTime expiresAt,
            LocalDateTime respondedAt) {}

    public record MatchingCase(
            int signupId,
            String status,
            String reason,
            int eligibleObserverCount,
            int remainingObserverCount,
            int unusedEligibleObserverCount,
            List<Candidate> previousRecommendations,
            List<Invitation> invitations,
            boolean canAssign,
            boolean canDefer,
            Deferral deferral) {}

    public record Deferral(String reason, String deferredBy, LocalDateTime deferredAt, String targetSemester) {}

    public Deferral deferral(int signupId) {
        return deferrals
                .findEligibilityDeferral(signupId)
                .map(r -> new Deferral(r.reason(), r.actor(), r.deferredAt(), r.target()))
                .orElse(null);
    }

    private void requireNotDeferred(int signupId) {
        if (deferral(signupId) != null) throw new ConflictException("This evaluation is deferred to next semester.");
    }

    public record AcState(Cycle cycle, List<Signup> signups, List<MatchingCase> matchingCases) {}

    /** The observation window a signup may choose: from {@code start} through any date up to {@code latestEnd}. */
    public record Window(
            LocalDate start, LocalDate latestEnd, LocalDate end, LocalDate signupDeadline, boolean signupOpen) {}

    private Cycle cycle(EvaluationCycleRepository.EligibilityCycleRow r) {
        LocalDate today = LocalDate.now(clock);
        return new Cycle(
                r.cycleId(),
                r.semesterId(),
                r.term() + " " + r.academicYear(),
                r.startDate(),
                r.signupDeadline(),
                r.observationDeadline(),
                r.feedbackDeadline(),
                "OPEN".equals(r.status()) && r.signupDeadline() != null && !today.isAfter(r.signupDeadline()),
                "OPEN".equals(r.status()) && r.observationDeadline() != null && !today.isAfter(r.observationDeadline()),
                "OPEN".equals(r.status())
                        && r.signupDeadline() != null
                        && today.isAfter(r.signupDeadline())
                        && r.observationDeadline() != null
                        && !today.isAfter(r.observationDeadline()));
    }

    /** The cycle of the semester that contains today, which the faculty and committee screens show. */
    public Cycle cycle() {
        LocalDate today = LocalDate.now(clock);
        var rows = evaluationCycles.findEligibilityCyclesContaining(today);
        if (rows.size() != 1)
            throw new ConflictException("Configure exactly one current semester and evaluation cycle.");
        return cycle(rows.getFirst());
    }

    private Cycle cycle(int cycleId) {
        return evaluationCycles
                .findEligibilityCycle(cycleId)
                .map(this::cycle)
                .orElseThrow(() -> new NotFoundException("Evaluation cycle not found."));
    }

    private EvaluationSignupRepository.SignupOwnerRow owner(int signupId) {
        return evaluationSignups.findOwner(signupId).orElseThrow(() -> new NotFoundException("Signup not found."));
    }

    private int cycleIdOf(int signupId) {
        return owner(signupId).cycleId();
    }

    // Serializes pool changes and request transitions, including concurrent duplicate submissions.
    private Cycle lockCycle(int cycleId) {
        evaluationCycles.findForUpdateByCycleId(cycleId).orElseThrow();
        return cycle(cycleId);
    }

    private Person person(int id) {
        return professors
                .findActivePerson(id)
                .map(r -> new Person(r.professorId(), r.name(), r.departmentName()))
                .orElseThrow(() -> new NotFoundException("Active professor not found."));
    }

    private List<Section> sections(int id, Cycle c) {
        return teachingHistory.findEligibilitySections(id, c.semesterId()).stream()
                .map(ObserverEligibilityService::section)
                .toList();
    }

    private static Section section(CourseSectionRepository.EligibilitySectionRow r) {
        return new Section(
                r.sectionId(),
                r.courseCode() + " " + r.courseNumber() + "." + r.sectionNumber(),
                r.courseName(),
                r.meetingDays(),
                r.startTime(),
                r.endTime());
    }

    private List<Signup> signups(Cycle c, boolean includeCandidates) {
        return evaluationSignups.findEligibilitySignups(c.cycleId()).stream()
                .map(r -> new Signup(
                        r.signupId(),
                        r.professorId(),
                        r.name(),
                        r.departmentName(),
                        section(r.section()),
                        r.windowStart(),
                        r.windowEnd(),
                        r.releasedAt() != null,
                        r.observerName(),
                        includeCandidates ? candidates(r.signupId()) : List.of()))
                .toList();
    }

    private List<Candidate> candidates(int signupId) {
        return candidates(signupId, "PROPOSED");
    }

    private List<Candidate> candidates(int signupId, String matchStatus) {
        return matches.findEligibilityCandidates(signupId, matchStatus).stream()
                .map(r -> candidate(r, signupId))
                .toList();
    }

    private Candidate candidate(ObserverRepository.EligibilityCandidateRow r, int signupId) {
        return new Candidate(
                r.observerId(),
                r.professorId(),
                r.name(),
                r.departmentName(),
                level(r.courseNumber()) + "-level",
                qualifyingCourses(r.professorId(), signupId));
    }

    /** Everyone who passes the eligibility rules for this signup, including those not drawn for its list. */
    public List<Candidate> eligibleObservers(int signupId) {
        Cycle c = cycle(cycleIdOf(signupId));
        return eligible(managedSignup(signupId, c), c).stream()
                .map(observerId -> candidate(
                        observers.findEligibilityCandidate(signupId, observerId).orElseThrow(), signupId))
                .sorted(Comparator.comparing(Candidate::name))
                .toList();
    }

    private List<String> qualifyingCourses(int professorId, int signupId) {
        return evaluationSignups.findQualifyingCourses(professorId, signupId).stream()
                .map(r -> level(r.courseNumber()) == level(r.targetNumber())
                        ? r.courseCode() + " " + r.courseNumber() + "." + r.sectionNumber() + " - " + r.term() + " "
                                + r.academicYear()
                        : null)
                .filter(Objects::nonNull)
                .toList();
    }

    public FacultyState facultyState(int id) {
        Cycle c = cycle();
        Person p = person(id);
        Signup signup = signups(c, true).stream()
                .filter(s -> s.professorId() == id)
                .findFirst()
                .orElse(null);
        if (signup != null && !signup.released())
            signup = new Signup(
                    signup.signupId(),
                    id,
                    signup.name(),
                    signup.department(),
                    signup.section(),
                    signup.windowStart(),
                    signup.windowEnd(),
                    false,
                    signup.observerName(),
                    List.of());
        var notifications = observerNotifications.findListNotificationsInCycle(id, c.cycleId()).stream()
                .map(n -> new Notification(
                        n.notificationId(),
                        n.message(),
                        n.createdAt().atZone(clock.getZone()).toOffsetDateTime()))
                .toList();
        List<Request> requests = requests(c);
        return new FacultyState(
                c,
                p,
                signup,
                requests.stream().filter(r -> r.professorId() == id).toList(),
                requests.stream()
                        .filter(r -> signupOwner(r.signupId()).professorId() == id)
                        .toList(),
                notifications);
    }

    public AcState acState() {
        return acState(cycle());
    }

    private AcState acState(Cycle c) {
        // Acceptance can create several observation rows for a signup. Count that signup only once.
        Map<Integer, Signup> distinctSignups = new LinkedHashMap<>();
        signups(c, true).forEach(s -> distinctSignups.putIfAbsent(s.signupId(), s));
        List<Signup> rows = List.copyOf(distinctSignups.values());
        LocalDateTime now = LocalDateTime.now(clock);
        Map<Integer, List<Invitation>> invitations = new HashMap<>();
        observerRequests.findEligibilityInvitations(c.cycleId()).forEach(r -> {
            LocalDateTime expires = r.expirationDate();
            String status = r.status();
            if ("CONFIRMED".equals(status) && r.assignedByAc()) status = "ASSIGNED";
            if ("PENDING".equals(status) && expires != null && !expires.isAfter(now)) status = "EXPIRED";
            invitations
                    .computeIfAbsent(r.signupId(), ignored -> new ArrayList<>())
                    .add(new Invitation(
                            r.requestId(),
                            r.observerId(),
                            r.observerName(),
                            status,
                            r.requestDate(),
                            expires,
                            r.responseDate()));
        });
        List<MatchingCase> cases = rows.stream()
                .map(s -> {
                    List<Invitation> history = invitations.getOrDefault(s.signupId(), List.of());
                    List<Candidate> previous = candidates(s.signupId(), "SUPERSEDED");
                    boolean matched = s.observerName() != null
                            || history.stream()
                                    .anyMatch(r -> "CONFIRMED".equals(r.status()) || "ASSIGNED".equals(r.status()));
                    boolean pending = history.stream().anyMatch(r -> "PENDING".equals(r.status()));
                    var eligible = eligible(s, c);
                    Set<Integer> requested = new HashSet<>();
                    history.forEach(r -> requested.add(r.observerId()));
                    int remaining = (int) eligible.stream()
                            .filter(id -> !requested.contains(id))
                            .count();
                    Set<Integer> recommended = new HashSet<>(requested);
                    s.candidates().forEach(candidate -> recommended.add(candidate.observerId()));
                    previous.forEach(candidate -> recommended.add(candidate.observerId()));
                    int unused = (int) eligible.stream()
                            .filter(id -> !recommended.contains(id))
                            .count();
                    String status;
                    String reason = null;
                    Set<Integer> unsuccessful = new HashSet<>();
                    history.stream()
                            .filter(r -> "DECLINED".equals(r.status()) || "EXPIRED".equals(r.status()))
                            .forEach(r -> unsuccessful.add(r.observerId()));
                    boolean recommendationsExhausted = !s.candidates().isEmpty()
                            && s.candidates().stream()
                                    .allMatch(candidate -> unsuccessful.contains(candidate.observerId()));
                    Deferral deferred = deferral(s.signupId());
                    if (deferred != null) status = "DEFERRED";
                    else if (matched) status = "MATCHED";
                    else if (pending) status = "AWAITING_RESPONSE";
                    else if (!s.released() && !previous.isEmpty()) {
                        status = "UNMATCHED";
                        reason = "RECOMMENDATIONS_AWAITING_RELEASE";
                    } else if (recommendationsExhausted) {
                        status = "UNMATCHED";
                        reason = "INVITATIONS_UNSUCCESSFUL";
                    } else if (eligible.isEmpty()) {
                        status = "UNMATCHED";
                        reason = "NO_ELIGIBLE_OBSERVERS";
                    } else status = "AWAITING_INVITATION";
                    return new MatchingCase(
                            s.signupId(),
                            status,
                            reason,
                            eligible.size(),
                            remaining,
                            unused,
                            previous,
                            List.copyOf(history),
                            deferred == null
                                    && c.candidateListsOpen()
                                    && c.requestsOpen()
                                    && !matched
                                    && !pending
                                    && !s.windowEnd().isBefore(LocalDate.now(clock))
                                    && s.released()
                                    && unused == 0
                                    && !eligible.isEmpty()
                                    && !recommended.isEmpty()
                                    && recommended.stream()
                                            .allMatch(id -> history.stream()
                                                    .anyMatch(r -> r.observerId() == id
                                                            && ("DECLINED".equals(r.status())
                                                                    || "EXPIRED".equals(r.status())))),
                            deferred == null
                                    && c.signupDeadline() != null
                                    && LocalDate.now(clock).isAfter(c.signupDeadline())
                                    && c.cycleId() == cycle().cycleId()
                                    && !matched
                                    && !pending
                                    && eligible(s, c, false).isEmpty(),
                            deferred);
                })
                .toList();
        return new AcState(c, rows, cases);
    }

    public record Deadlines(LocalDate signupDeadline, LocalDate observationDeadline, LocalDate feedbackDeadline) {}

    public Cycle updateDeadlines(int cycleId, Deadlines dates) {
        cycle(cycleId);
        Cycle previous = lockCycle(cycleId);
        if (cycle().cycleId() != cycleId)
            throw new ConflictException("Only the current evaluation cycle's deadlines can be edited.");
        if (dates == null
                || dates.signupDeadline() == null
                || dates.observationDeadline() == null
                || dates.feedbackDeadline() == null)
            throw new IllegalArgumentException("All three deadlines are required.");
        if (dates.signupDeadline().isBefore(previous.startDate())
                || !dates.observationDeadline().isAfter(dates.signupDeadline())
                || !dates.feedbackDeadline().isAfter(dates.observationDeadline()))
            throw new IllegalArgumentException(
                    "Signup must be on or after semester start, observation must be after signup, and feedback must be after observation.");
        int scheduledOutside = observations.countScheduledOutsideDeadlines(
                cycleId, dates.signupDeadline(), dates.observationDeadline());
        if (scheduledOutside > 0)
            throw new ConflictException(
                    "These deadlines would exclude an existing observation date. Reschedule the observation before changing these deadlines.");
        LocalDate start = dates.signupDeadline().plusDays(1);
        var windows = listStates.findWindowsInCycle(cycleId);
        var revisedWindows = new ArrayList<Map.Entry<Integer, LocalDate>>();
        for (var window : windows) {
            LocalDate end = window.windowEnd();
            if (Objects.equals(end, previous.observationDeadline())) end = dates.observationDeadline();
            else if (end.isAfter(dates.observationDeadline())) end = dates.observationDeadline();
            if (end.isBefore(start))
                throw new ConflictException(
                        "The signup deadline would leave an existing signup without an observation window.");
            revisedWindows.add(Map.entry(window.signupId(), end));
        }
        for (var window : revisedWindows) {
            listStates.updateWindow(window.getKey(), start, window.getValue());
        }
        evaluationCycles.updateDeadlines(
                cycleId, dates.signupDeadline(), dates.observationDeadline(), dates.feedbackDeadline());
        return cycle(cycleId);
    }

    private static LocalDate windowStart(Cycle c) {
        return c.signupDeadline() == null ? null : c.signupDeadline().plusDays(1);
    }

    /** The window a signup in this cycle may choose, with the end date already saved for {@code signupId}, if any. */
    public Window window(int cycleId, Integer signupId) {
        Cycle c = cycle(cycleId);
        return new Window(
                windowStart(c),
                c.observationDeadline(),
                signupId == null ? null : windowEnd(signupId),
                c.signupDeadline(),
                c.signupOpen());
    }

    public LocalDate windowEnd(int signupId) {
        return listStates.findById(signupId).map(ObserverListState::windowEnd).orElse(null);
    }

    /**
     * Checks the signup deadline and observation window for a signup that was just saved, then adds the professor to
     * the cycle's observer pool and fills candidate lists. A null end keeps the saved window, or for a new signup runs
     * through the observation deadline.
     */
    public void signedUp(int signupId, LocalDate end) {
        var signup = owner(signupId);
        Cycle c = lockCycle(signup.cycleId());
        if (!c.signupOpen()) throw new ConflictException("The signup deadline has passed or this cycle is closed.");
        int professorId = signup.professorId();
        Section section = section(
                courseSections.findEligibilitySection(signup.sectionId()).orElseThrow());
        requireSchedule(section);
        LocalDate saved = windowEnd(signupId);
        LocalDate start = windowStart(c);
        if (end == null) end = saved != null ? saved : c.observationDeadline();
        if (start == null
                || end == null
                || c.observationDeadline() == null
                || end.isBefore(start)
                || end.isAfter(c.observationDeadline()))
            throw new IllegalArgumentException(
                    "The observation window must start the day after the signup deadline and end by the observation deadline.");
        if (!hasMeeting(section, start, end, Set.of()))
            throw new IllegalArgumentException("The window contains no class meetings.");
        if (saved == null) listStates.insertWindow(signupId, start, end);
        else listStates.updateWindow(signupId, start, end);
        if (observers.findByProfessorIdAndCycleId(professorId, c.cycleId()).isEmpty())
            observers.save(new Observer(null, professorId, c.cycleId(), true));
        else observers.activate(professorId, c.cycleId());
        refresh(c);
    }

    /** Adds to incomplete lists without reshuffling existing candidates or changing released lists. */
    public AcState generate() {
        Cycle c = lockCycle(cycle().cycleId());
        refresh(c);
        return acState(c);
    }

    /** Draws a fresh list only after every current recommendation was declined or expired. */
    public AcState regenerate(int signupId) {
        requireNotDeferred(signupId);
        var owner = owner(signupId);
        // Serialize against invitation sends, assignments and observer responses.
        professors.findForUpdateByProfessorId(owner.professorId()).orElseThrow();
        evaluationSignups.findForUpdateBySignupId(signupId).orElseThrow();
        Cycle c = lockCycle(owner.cycleId());
        if (!c.requestsOpen()) throw new ConflictException("The observation period is closed.");
        Signup signup = managedSignup(signupId, c);
        if (signup.windowEnd().isBefore(LocalDate.now(clock)))
            throw new ConflictException("This signup's observation window has closed.");
        if (!signup.released())
            throw new ConflictException("Review and send the current candidate list before generating another set.");
        MatchingCase state = acState(c).matchingCases().stream()
                .filter(row -> row.signupId() == signupId)
                .findFirst()
                .orElseThrow();
        if (!"UNMATCHED".equals(state.status()) || !"INVITATIONS_UNSUCCESSFUL".equals(state.reason()))
            throw new ConflictException(
                    "Every recommended observer must decline or expire, with no confirmed observer or pending invitation, before generating another set.");
        var pool = new ArrayList<>(unusedEligible(signup, c));
        if (pool.isEmpty())
            throw new ConflictException("No additional eligible observers remain. This case still needs AC review.");
        Collections.shuffle(pool);
        matches.supersedeProposed(signupId);
        matches.saveAll(pool.stream()
                .limit(LIST_SIZE)
                .map(observerId -> proposal(signupId, observerId))
                .toList());
        listStates.clearReleasedAt(signupId);
        return acState(c);
    }

    private List<Integer> unusedEligible(Signup signup, Cycle c) {
        Set<Integer> used = new HashSet<>(matches.findMatchedOrRequestedObserverIds(signup.signupId()));
        return eligible(signup, c).stream().filter(id -> !used.contains(id)).toList();
    }

    private void refresh(Cycle c) {
        if (!c.requestsOpen()) return;
        for (Signup s : signups(c, true)) {
            if (deferral(s.signupId()) != null || s.released() || s.candidates().size() >= LIST_SIZE) continue;
            var eligible = new ArrayList<>(unusedEligible(s, c));
            Collections.shuffle(eligible);
            matches.saveAll(eligible.stream()
                    .limit(LIST_SIZE - s.candidates().size())
                    .map(observerId -> proposal(s.signupId(), observerId))
                    .toList());
        }
    }

    private ObserverMatch proposal(int signupId, int observerId) {
        return new ObserverMatch(null, signupId, observerId, LocalDate.now(clock), "PROPOSED");
    }

    static int level(String number) {
        if (number == null || !number.matches("[1-9][0-9]{3}")) return -1;
        return Integer.parseInt(number) / 1000 * 1000;
    }

    private List<Integer> eligible(Signup signup, Cycle c) {
        return eligible(signup, c, true);
    }

    private List<Integer> eligible(Signup signup, Cycle c, boolean remainingDatesOnly) {
        var target = professors
                .findEligibilityTarget(signup.professorId(), signup.section().sectionId())
                .orElseThrow();
        int targetLevel = level(target.courseNumber());
        if (targetLevel < 0) return List.of();
        var pool = observers.findEligibilityPool(c.cycleId(), target.departmentId(), signup.professorId());
        List<Integer> result = new ArrayList<>();
        for (var row : pool) {
            int id = row.professorId();
            if (teachesLevel(id, c, target.departmentId(), targetLevel)
                    && scheduleWorks(signup, id, c, remainingDatesOnly)) result.add(row.observerId());
        }
        return result;
    }

    /** Rule (c): the observer taught a course at the same level, in the observee's department, in the last two years. */
    private boolean teachesLevel(int professorId, Cycle c, int departmentId, int targetLevel) {
        return teachingHistory
                .findCourseNumbersTaught(
                        professorId, c.startDate(), c.startDate().minusYears(2), departmentId)
                .stream()
                .anyMatch(n -> level(n) == targetLevel);
    }

    /** Rule (d): the observer is free, on at least one class date, for the observee's requested days and times. */
    private boolean scheduleWorks(Signup signup, int professorId, Cycle c, boolean remainingDatesOnly) {
        Set<DayOfWeek> blocked = EnumSet.noneOf(DayOfWeek.class);
        for (Section taught : sections(professorId, c)) {
            if (!validSchedule(taught)) return false;
            if (taught.startTime().isBefore(signup.section().endTime())
                    && signup.section().startTime().isBefore(taught.endTime()))
                blocked.addAll(days(taught.meetingDays()));
        }
        return hasMeeting(
                signup.section(),
                remainingDatesOnly && signup.windowStart().isBefore(LocalDate.now(clock))
                        ? LocalDate.now(clock)
                        : signup.windowStart(),
                signup.windowEnd(),
                blocked);
    }

    /** Counts of observer assignments that break each eligibility rule. */
    public record Audit(int checked, int samePerson, int otherDepartment, int differentLevel, int scheduleConflict) {
        public int incorrect() {
            return samePerson + otherDepartment + differentLevel + scheduleConflict;
        }
    }

    /**
     * Re-validates every observer assignment in the current cycle against the eligibility rules. Each assignment is
     * counted once, under the first rule it breaks: (a) same professor, (b) other department, (c) different level,
     * (d) schedule conflict.
     */
    public Audit audit() {
        Cycle c = cycle();
        Map<Integer, Signup> byId = new HashMap<>();
        signups(c, false).forEach(s -> byId.put(s.signupId(), s));
        int checked = 0, same = 0, department = 0, level = 0, schedule = 0;
        for (var pair : observations.findAssignedPairs(c.cycleId())) {
            Signup signup = byId.get(pair.signupId());
            var observer = observers.findById(pair.observerId()).orElse(null);
            if (signup == null || observer == null) continue;
            var target = professors
                    .findEligibilityTarget(
                            signup.professorId(), signup.section().sectionId())
                    .orElse(null);
            var observerProfessor = professors.findById(observer.professorId()).orElse(null);
            if (target == null || observerProfessor == null) continue;
            checked++;
            if (observer.professorId() == signup.professorId()) same++;
            else if (!target.departmentId().equals(observerProfessor.departmentId())) department++;
            else if (level(target.courseNumber()) < 0
                    || !teachesLevel(observer.professorId(), c, target.departmentId(), level(target.courseNumber())))
                level++;
            else if (!scheduleWorks(signup, observer.professorId(), c, false)) schedule++;
        }
        return new Audit(checked, same, department, level, schedule);
    }

    static Set<DayOfWeek> days(String value) {
        if (value == null) return Set.of();
        String text = value.toUpperCase(Locale.ROOT).trim();
        String[] full = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"};
        String[] shortNames = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};
        String[] codes = {"M", "T", "W", "R", "F", "S", "U"};
        for (int i = 0; i < 7; i++) text = text.replace(full[i], codes[i]);
        for (int i = 0; i < 7; i++) text = text.replace(shortNames[i], codes[i]);
        text = text.replace("TH", "R").replaceAll("[\\s,;/&-]", "");
        if (text.isEmpty() || !text.matches("[MTWRFSU]+")) return Set.of();
        Set<DayOfWeek> result = EnumSet.noneOf(DayOfWeek.class);
        for (int i = 0; i < 7; i++) if (text.contains(codes[i])) result.add(DayOfWeek.of(i + 1));
        return result;
    }

    private static boolean validSchedule(Section s) {
        return !days(s.meetingDays()).isEmpty()
                && s.startTime() != null
                && s.endTime() != null
                && s.startTime().isBefore(s.endTime());
    }

    private static void requireSchedule(Section s) {
        if (!validSchedule(s)) throw new IllegalArgumentException("This section has an incomplete meeting schedule.");
    }

    private static boolean hasMeeting(Section s, LocalDate start, LocalDate end, Set<DayOfWeek> blocked) {
        if (!validSchedule(s)) return false;
        var available = EnumSet.copyOf(days(s.meetingDays()));
        available.removeAll(blocked);
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1))
            if (available.contains(d.getDayOfWeek())) return true;
        return false;
    }

    public void release(int signupId) {
        requireNotDeferred(signupId);
        Cycle c = lockCycle(cycleIdOf(signupId));
        if (!c.requestsOpen()) throw new ConflictException("The observation period is closed.");
        Signup s = managedSignup(signupId, c);
        if (s.released()) return;
        if (!c.candidateListsOpen())
            throw new ConflictException("Candidate lists can only be sent after the signup deadline ends.");
        if (s.observerName() != null) throw new ConflictException("An observer is already assigned to this signup.");
        if (s.candidates().isEmpty())
            throw new ConflictException("No eligible observers. The committee must resolve this signup.");
        var eligible = eligible(s, c);
        if (s.candidates().stream().anyMatch(p -> !eligible.contains(p.observerId())))
            throw new ConflictException(
                    "A candidate is no longer eligible. Review the teaching schedule before release.");
        listStates.markReleased(signupId, LocalDateTime.now(clock));
        boolean replacement = !candidates(signupId, "SUPERSEDED").isEmpty();
        notify(
                s.professorId(),
                signupId,
                replacement
                        ? "A new set of observer recommendations is ready. Your previous invitations were unsuccessful. Choose from the new list to send another invitation."
                        : "Your generated observer list is ready. Choose an observer to request.");
    }

    /**
     * The committee confirms an eligible professor as the signup's observer, recording the same confirmed request and
     * observation an accepted request would, without the observee asking or the observer responding.
     */
    public void assign(int signupId, int observerId) {
        var owner = owner(signupId);
        // Same lock order as course selection and request sends: professor, signup, then cycle.
        professors.findForUpdateByProfessorId(owner.professorId()).orElseThrow();
        evaluationSignups.findForUpdateBySignupId(signupId).orElseThrow();
        Cycle c = lockCycle(owner.cycleId());
        if (!c.requestsOpen()) throw new ConflictException("The observation period is closed.");
        Signup s = managedSignup(signupId, c);
        requireNotDeferred(signupId);
        if (s.observerName() != null) throw new ConflictException("An observer is already assigned to this signup.");
        var pending = requests(c).stream()
                .filter(r -> r.signupId() == signupId && "PENDING".equals(r.status()))
                .findFirst();
        if (pending.isPresent())
            throw new ConflictException(
                    s.name() + " is waiting for " + pending.get().observerName()
                            + " to respond. Assign an observer if the request is declined or expires.");
        if (!eligible(s, c).contains(observerId))
            throw new ConflictException("This professor is not eligible to observe this signup.");
        MatchingCase matching = acState(c).matchingCases().stream()
                .filter(row -> row.signupId() == signupId)
                .findFirst()
                .orElseThrow();
        if (!matching.canAssign())
            throw new ConflictException(
                    "Every generated invitation must be declined or expired and no additional eligible observers can remain before manual assignment.");
        var template = templates.findFirstByActiveStatusTrueOrderByVersionDescTemplateIdDesc();
        if (template.isEmpty()) throw new ConflictException("Configure an active observation template first.");
        LocalDateTime now = LocalDateTime.now(clock);
        observerRequests.save(new ObserverRequest(
                null,
                signupId,
                observerId,
                now,
                now.plus(ObserverRequestService.DEFAULT_RESPONSE_WINDOW),
                now,
                "CONFIRMED",
                true));
        observations.save(observation(signupId, observerId, template.get().templateId(), null, null, "ACCEPTED"));
        Person observer = person(observers.findById(observerId).orElseThrow().professorId());
        notify(
                s.professorId(),
                signupId,
                "The Assessment Committee assigned " + observer.name() + " to observe "
                        + s.section().label() + ".");
        notify(
                observer.professorId(),
                signupId,
                "The Assessment Committee assigned you to observe " + s.name() + "'s "
                        + s.section().label() + " ("
                        + s.section().meetingDays() + " " + s.section().startTime() + "-"
                        + s.section().endTime() + ").");
    }

    public void defer(int signupId, int actorProfessorId, String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 1000)
            throw new IllegalArgumentException("Provide a reason of 1 to 1000 characters.");
        var owner = signupOwner(signupId);
        professors.findForUpdateByProfessorId(owner.professorId()).orElseThrow();
        evaluationSignups.findForUpdateBySignupId(signupId).orElseThrow();
        Cycle c = lockCycle(owner.cycleId());
        MatchingCase matching = acState(c).matchingCases().stream()
                .filter(row -> row.signupId() == signupId)
                .findFirst()
                .orElseThrow();
        if (!matching.canDefer())
            throw new ConflictException(
                    "Only an unmatched faculty member with no eligible observers can be deferred after signup closes.");
        int next = semesters
                .findNextSemesterId(c.semesterId())
                .orElseThrow(
                        () -> new ConflictException("Configure the next semester before deferring this evaluation."));
        deferrals.insertDeferral(
                signupId, owner.professorId(), next, reason.trim(), actorProfessorId, LocalDateTime.now(clock));
        evaluationSignups.markDeferred(signupId);
        observees.markDeferredBySignupId(signupId);
        notify(
                owner.professorId(),
                signupId,
                "The Assessment Committee marked your evaluation Due next semester ("
                        + deferral(signupId).targetSemester() + "). Reason: " + reason.trim());
    }

    private Signup managedSignup(int id, Cycle c) {
        return signups(c, true).stream()
                .filter(s -> s.signupId() == id)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Signup not found."));
    }

    private EvaluationSignupRepository.SignupOwnerRow signupOwner(int id) {
        return evaluationSignups.findOwner(id).orElseThrow(() -> new EmptyResultDataAccessException(1));
    }

    private boolean released(int signupId) {
        return listStates.existsBySignupIdAndReleasedAtIsNotNull(signupId);
    }

    /** Why the observee cannot request an observer for this signup yet, or null if nothing here prevents it. */
    public String requestBlocker(int signupId) {
        if (!manages(signupId)) return null;
        if (!cycle(cycleIdOf(signupId)).requestsOpen()) return "The observation period is closed.";
        if (!released(signupId)) return "Your observer list is awaiting committee review.";
        return null;
    }

    /** The observee sees a generated list only after the committee releases it. */
    public boolean candidatesVisible(int signupId) {
        return !manages(signupId) || released(signupId);
    }

    /** Rejects a listed observer whose schedule or pool membership changed after the list was generated. */
    public void requireEligible(int signupId, int observerId) {
        if (!manages(signupId)) return;
        Cycle c = cycle(cycleIdOf(signupId));
        if (!eligible(managedSignup(signupId, c), c).contains(observerId))
            throw new ConflictException("This observer is no longer eligible for this signup.");
    }

    public void requestSent(int signupId, int observerId) {
        if (!manages(signupId)) return;
        Signup s = managedSignup(signupId, cycle(cycleIdOf(signupId)));
        int recipient = observers.findById(observerId).orElseThrow().professorId();
        notify(
                recipient,
                signupId,
                s.name() + " requested an observation of " + s.section().label() + " ("
                        + s.section().meetingDays() + " " + s.section().startTime() + "-"
                        + s.section().endTime() + ").");
    }

    private List<Request> requests(Cycle c) {
        observerRequests.expirePendingInCycle(LocalDateTime.now(clock), c.cycleId());
        return observerRequests.findEligibilityRequests(c.cycleId()).stream()
                .map(r -> new Request(
                        r.requestId(),
                        r.signupId(),
                        r.professorId(),
                        r.observeeName(),
                        r.observerName(),
                        section(r.section()),
                        r.windowStart(),
                        r.windowEnd(),
                        r.status(),
                        r.observationDate(),
                        r.assignedByAc()))
                .toList();
    }

    /** Each acceptance records that observer's observation without changing other requests. */
    public void respond(int professorId, int requestId, boolean accept) {
        respond(professorId, requestId, accept, null);
    }

    public void respond(int professorId, int requestId, boolean accept, LocalDate observationDate) {
        var request = observerRequests
                .findById(requestId)
                .orElseThrow(() -> new NotFoundException("Observation request not found."));
        int signupId = request.signupId();
        Cycle c = lockCycle(cycleIdOf(signupId));
        Request r = requests(c).stream()
                .filter(x -> x.requestId() == requestId && x.professorId() == professorId)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Observation request not found."));
        if (!c.requestsOpen() || !"PENDING".equals(r.status()))
            throw new ConflictException("This request is no longer pending or the observation period is closed.");
        int observer = request.observerId();
        if (accept) {
            if (!eligible(managedSignup(signupId, c), c).contains(observer))
                throw new ConflictException("You are no longer eligible for this observation.");
            if (observations.findBySignupIdAndObserverId(signupId, observer).isPresent())
                throw new ConflictException("This observer already has an observation for this signup.");
        }
        var template = templates.findFirstByActiveStatusTrueOrderByVersionDescTemplateIdDesc();
        if (accept && !availableDates(professorId, requestId).contains(observationDate))
            throw new IllegalArgumentException("Choose an available class date within the observation window.");
        if (accept && template.isEmpty())
            throw new ConflictException("Configure an active observation template first.");
        observerRequests.updateStatusAndResponseDate(
                requestId, accept ? "CONFIRMED" : "DECLINED", LocalDateTime.now(clock));
        if (accept)
            observations.save(observation(
                    signupId,
                    observer,
                    template.get().templateId(),
                    observationDate,
                    r.section().startTime(),
                    "SCHEDULED"));
        notify(
                signupOwner(signupId).professorId(),
                signupId,
                r.observerName() + (accept ? " accepted" : " declined") + " your observation request for "
                        + r.section().label() + ".");
    }

    private static Observation observation(
            int signupId, int observerId, int templateId, LocalDate date, LocalTime startTime, String status) {
        return new Observation(
                null, signupId, observerId, templateId, date, startTime, status, false, false, null, null, null, null);
    }

    private Request schedulingRequest(int professorId, int requestId) {
        int signupId = observerRequests
                .findById(requestId)
                .map(ObserverRequest::signupId)
                .orElseThrow(() -> new NotFoundException("Observation request not found."));
        Cycle c = lockCycle(cycleIdOf(signupId));
        Request r = requests(c).stream()
                .filter(x -> x.requestId() == requestId && x.professorId() == professorId)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Observation request not found."));
        if (!c.requestsOpen() || !("PENDING".equals(r.status()) || "CONFIRMED".equals(r.status())))
            throw new ConflictException("This request cannot be scheduled.");
        return r;
    }

    public List<LocalDate> availableDates(int professorId, int requestId) {
        Request r = schedulingRequest(professorId, requestId);
        Cycle c = cycle(cycleIdOf(r.signupId()));
        requireSchedule(r.section());
        var taught = sections(professorId, c);
        var result = new ArrayList<LocalDate>();
        LocalDate start = r.windowStart().isAfter(LocalDate.now(clock)) ? r.windowStart() : LocalDate.now(clock);
        LocalDate end = r.windowEnd().isBefore(c.observationDeadline()) ? r.windowEnd() : c.observationDeadline();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (!days(r.section().meetingDays()).contains(d.getDayOfWeek())) continue;
            if (d.equals(LocalDate.now(clock)) && !r.section().startTime().isAfter(LocalTime.now(clock))) continue;
            DayOfWeek day = d.getDayOfWeek();
            if (taught.stream()
                    .anyMatch(t -> !validSchedule(t)
                            || (days(t.meetingDays()).contains(day)
                                    && t.startTime().isBefore(r.section().endTime())
                                    && r.section().startTime().isBefore(t.endTime())))) continue;
            int conflicts = observations.countObserverTimeConflicts(
                    professorId,
                    r.signupId(),
                    d,
                    r.section().endTime(),
                    r.section().startTime());
            if (conflicts == 0) result.add(d);
        }
        return result;
    }

    public void scheduleObservation(int professorId, int requestId, LocalDate observationDate) {
        Request r = schedulingRequest(professorId, requestId);
        if (!"CONFIRMED".equals(r.status())) throw new ConflictException("Accept the request before scheduling it.");
        if (!availableDates(professorId, requestId).contains(observationDate))
            throw new IllegalArgumentException("Choose an available class date within the observation window.");
        int changed = observations.scheduleAcceptedObservation(
                r.signupId(), requestId, observationDate, r.section().startTime());
        if (changed != 1) throw new ConflictException("This observation has already been scheduled or started.");
        notify(
                signupOwner(r.signupId()).professorId(),
                r.signupId(),
                r.observerName() + " scheduled your observation for " + observationDate + ".");
    }

    private void notify(int professorId, int signupId, String message) {
        observerNotifications.save(
                new ObserverNotification(null, professorId, signupId, message, LocalDateTime.now(clock)));
    }
}
