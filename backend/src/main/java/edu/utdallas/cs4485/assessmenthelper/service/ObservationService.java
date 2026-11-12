package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.entity.ObservationResponse;
import edu.utdallas.cs4485.assessmenthelper.entity.Observee;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverNotification;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationSignupRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationAuditLogRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationRepository.ObservationDetailRow;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationResponseRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserveeRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverNotificationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationTemplateService.Criterion;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationTemplateService.Template;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Observation forms as the observer fills them in and the observee reviews them. The observer sees a form in every
 * status: they start it, save drafts, submit it (their sign-off), and can reopen it read-only afterwards. The observee
 * may read it once it is submitted, then adds comments and signs off, which completes and locks the record. An
 * Assessment Committee member may read a completed record in their school, never edit it, and nobody else may see it.
 * Each of these returns the same {@link Detail}. An observation row is
 * created when the observer accepts a request, in {@link ObserverEligibilityService#respond}.
 */
@Service
@Transactional
public class ObservationService {
    private static final int MAX_COMMENTS = 4000;

    private final ObservationRepository observations;
    private final ObservationResponseRepository observationResponses;
    private final ObservationAuditLogRepository auditLog;
    private final ObserverNotificationRepository notifications;
    private final ProfessorRepository professors;
    private final ObservationTemplateService templates;
    private final EvaluationSignupRepository signups;
    private final ObserveeRepository observees;
    private final Clock clock;

    public ObservationService(
            ObservationRepository observations,
            ObservationResponseRepository observationResponses,
            ObservationAuditLogRepository auditLog,
            ObserverNotificationRepository notifications,
            ProfessorRepository professors,
            ObservationTemplateService templates,
            EvaluationSignupRepository signups,
            ObserveeRepository observees,
            Clock clock) {
        this.observations = observations;
        this.observationResponses = observationResponses;
        this.auditLog = auditLog;
        this.notifications = notifications;
        this.professors = professors;
        this.templates = templates;
        this.signups = signups;
        this.observees = observees;
        this.clock = clock;
    }

    /**
     * ACCEPTED or SCHEDULED, then IN_PROGRESS once started and SUBMITTED once the observer submits (the observer's
     * sign-off), when the observee may read it and respond. COMPLETED once the observee signs off: the record is then
     * locked, and the observer, the observee and the school's Assessment Committee can still open it read-only.
     */
    public enum Status {
        ACCEPTED,
        SCHEDULED,
        IN_PROGRESS,
        SUBMITTED,
        COMPLETED
    }

    public enum ViewerRole {
        OBSERVER,
        OBSERVEE,
        COMMITTEE
    }

    /** What the audit log records about an observation. Entries are only ever added. */
    public enum AuditEvent {
        STARTED,
        EDITED,
        SUBMITTED,
        OBSERVEE_COMMENTED,
        OBSERVEE_SIGNED,
        LOCKED,
        VIEWED,
        FORWARDED
    }

    /** A stored answer: a rating (RATING questions only) and comments, either of which may be missing in a draft. */
    public record Answer(int criterionId, Integer rating, String comments) {}

    public record Person(int professorId, String name, String department) {}

    public record Course(
            int sectionId,
            String label,
            String courseName,
            String meetingDays,
            LocalTime startTime,
            LocalTime endTime,
            String location) {}

    public record Summary(
            int observationId,
            ViewerRole role,
            Status status,
            String observeeName,
            String observerName,
            String courseLabel,
            String semesterLabel,
            int cycleId,
            LocalDate observationDate,
            LocalTime startTime,
            LocalDateTime submittedAt) {}

    /**
     * The whole form. The template is the one the observation was created with, which may no longer be active, and
     * submittedAt stays null until the observer submits.
     */
    public record Detail(
            int observationId,
            Status status,
            ViewerRole viewerRole,
            Person observee,
            Person observer,
            Course course,
            String semesterLabel,
            int cycleId,
            LocalDate observationDate,
            LocalTime startTime,
            ObservationTemplateService.Template template,
            List<Answer> responses,
            LocalDateTime submittedAt,
            boolean canStart,
            boolean canEdit,
            boolean locked,
            boolean canSign,
            String observeeComments,
            LocalDateTime observeeSignedAt) {}

    /** One observation with its people, course and semester, before deciding who may see it. */
    private record Row(
            int observationId,
            int signupId,
            Status status,
            int templateId,
            int cycleId,
            String semesterLabel,
            Person observee,
            Person observer,
            Course course,
            LocalDate observationDate,
            LocalTime startTime,
            LocalDateTime submittedAt,
            String observeeComments,
            LocalDateTime observeeSignedAt,
            int schoolId) {
        /**
         * The record is locked once the observee has signed off, which completes it. From then on it is read-only for
         * everyone. Between the observer's submit and the observee's sign-off the observer's answers are already frozen,
         * but the observee can still comment, so that is not locked yet.
         */
        boolean locked() {
            return status == Status.COMPLETED;
        }
    }

    private static Row row(ObservationDetailRow r) {
        return new Row(
                r.observationId(),
                r.signupId(),
                Status.valueOf(r.status()),
                r.templateId(),
                r.cycleId(),
                r.term() + " " + r.academicYear(),
                new Person(r.observeeProfessorId(), r.observeeName(), r.observeeDepartment()),
                new Person(r.observerProfessorId(), r.observerName(), r.observerDepartment()),
                new Course(
                        r.sectionId(),
                        r.courseCode() + " " + r.courseNumber() + "." + r.sectionNumber(),
                        r.courseName(),
                        r.meetingDays(),
                        r.sectionStart(),
                        r.sectionEnd(),
                        r.location()),
                r.observationDate(),
                r.observationStart(),
                r.observerSignedAt(),
                r.observeeComments(),
                r.observeeSignedAt(),
                r.observeeSchoolId());
    }

    private Row find(int observationId) {
        return observations
                .findDetailByObservationId(observationId)
                .map(ObservationService::row)
                .orElseThrow(() -> new NotFoundException("Observation not found."));
    }

    // Writes call this first, so double submits and a save racing a submit run one after the other.
    private Row lock(int observationId) {
        if (observations.findForUpdateByObservationId(observationId).isEmpty())
            throw new NotFoundException("Observation not found.");
        return find(observationId);
    }

    private static boolean isObserver(Row o, AccountPrincipal principal) {
        return Objects.equals(principal.user().professorId(), o.observer().professorId());
    }

    private static boolean isObservee(Row o, AccountPrincipal principal) {
        return Objects.equals(principal.user().professorId(), o.observee().professorId());
    }

    /**
     * The role {@code principal} sees the observation in, or null if they may not see it now. The observer always may,
     * and the observee once the observer has submitted, so never a draft. A committee member may read only a completed
     * record across all schools. Nobody else may.
     */
    private static ViewerRole viewerRole(Row o, AccountPrincipal principal) {
        if (isObserver(o, principal)) return ViewerRole.OBSERVER;
        if (isObservee(o, principal) && (o.status() == Status.SUBMITTED || o.status() == Status.COMPLETED))
            return ViewerRole.OBSERVEE;
        if (committeeMayRead(o, principal)) return ViewerRole.COMMITTEE;
        return null;
    }

    // Committee members read completed records across all schools.
    private static boolean committeeMayRead(Row o, AccountPrincipal principal) {
        return principal.isCommitteeMember() && o.status() == Status.COMPLETED;
    }

    /**
     * The form as {@code principal} sees it. Every time someone opens a locked record that is written to the audit log,
     * so this cannot be a read-only transaction. A refused request is not logged, since nothing was shown.
     */
    public Detail detail(int observationId, AccountPrincipal principal) {
        Row o = find(observationId);
        Detail detail = detail(o, principal);
        if (o.locked()) log(observationId, AuditEvent.VIEWED, principal, null);
        return detail;
    }

    /** The form as {@code principal} sees it, with what they may do next. */
    private Detail detail(Row o, AccountPrincipal principal) {
        ViewerRole role = viewerRole(o, principal);
        if (role == null) throw new AccessDeniedException("You do not have access to this observation.");
        return new Detail(
                o.observationId(),
                o.status(),
                role,
                o.observee(),
                o.observer(),
                o.course(),
                o.semesterLabel(),
                o.cycleId(),
                o.observationDate(),
                o.startTime(),
                templates.template(o.templateId()),
                responses(o.observationId()),
                o.submittedAt(),
                role == ViewerRole.OBSERVER && (o.status() == Status.ACCEPTED || o.status() == Status.SCHEDULED),
                role == ViewerRole.OBSERVER && o.status() == Status.IN_PROGRESS,
                o.locked(),
                role == ViewerRole.OBSERVEE && o.status() == Status.SUBMITTED,
                o.observeeComments(),
                o.observeeSignedAt());
    }

    private List<Answer> responses(int observationId) {
        return observationResponses.findAnswersByObservationId(observationId).stream()
                .map(r -> new Answer(r.criterionId(), r.rating(), r.comments()))
                .toList();
    }

    /**
     * The observer opens the form at the start of the class. On or after the planned day the planned date is kept, with
     * the planned time if there is one. Before the planned day, or with no date planned, the class is being observed
     * now, so the form records today and the current minute. Starting a form that is already open changes nothing.
     */
    public Detail start(int observationId, AccountPrincipal principal) {
        Row o = lock(observationId);
        if (!isObserver(o, principal)) throw new AccessDeniedException("Only the observer can start this observation.");
        if (o.status() == Status.ACCEPTED || o.status() == Status.SCHEDULED) {
            LocalDateTime now = LocalDateTime.now(clock);
            LocalTime minute = now.toLocalTime().truncatedTo(ChronoUnit.MINUTES);
            boolean early = o.observationDate() == null || o.observationDate().isAfter(now.toLocalDate());
            observations.updateStarted(
                    Status.IN_PROGRESS.name(),
                    early ? now.toLocalDate() : o.observationDate(),
                    early || o.startTime() == null ? minute : o.startTime(),
                    observationId);
            log(observationId, AuditEvent.STARTED, principal, null);
        } else if (o.status() != Status.IN_PROGRESS)
            throw new ConflictException("This observation has already been submitted.");
        return detail(find(observationId), principal);
    }

    /** Saves the observer's draft. The answers sent replace every stored answer, and may leave questions blank. */
    public Detail saveResponses(int observationId, List<Answer> answers, AccountPrincipal principal) {
        Row o = lock(observationId);
        requireEditable(o, principal);
        store(observationId, checked(answers, templates.template(o.templateId())));
        log(observationId, AuditEvent.EDITED, principal, null);
        return detail(find(observationId), principal);
    }

    /**
     * Saves the final answers and submits the form, once every required question is answered. This is the observer's
     * sign-off: their answers are frozen from then on. The observee can now read the form and is asked to review it and
     * sign off. The record is not locked until they do, see {@link #observeeSign}.
     */
    public Detail submit(int observationId, List<Answer> answers, AccountPrincipal principal) {
        Row o = lock(observationId);
        requireEditable(o, principal);
        Template template = templates.template(o.templateId());
        List<Answer> checked = checked(answers, template);
        Map<Integer, Answer> byQuestion = new HashMap<>();
        checked.forEach(a -> byQuestion.put(a.criterionId(), a));
        List<String> unanswered = template.criteria().stream()
                .filter(q -> q.required() && !answered(q, byQuestion.get(q.criterionId())))
                .map(Criterion::criterionText)
                .toList();
        if (!unanswered.isEmpty())
            throw new IllegalArgumentException(
                    "Answer every required question before submitting: " + String.join(", ", unanswered) + ".");
        store(observationId, checked);
        observations.updateObserverSubmitted(Status.SUBMITTED.name(), now(), observationId);
        log(observationId, AuditEvent.SUBMITTED, principal, null);
        notify(
                o.observee().professorId(),
                o.signupId(),
                o.observer().name() + " submitted the observation of "
                        + o.course().label() + ". Please review it and sign off.");
        return detail(find(observationId), principal);
    }

    /**
     * The observee reviews the submitted form, optionally leaves comments, and signs off. That completes the
     * observation and locks the record. Only the observee can do this, and only once the observer has submitted.
     * The comments are checked first, so nothing changes if they are too long. The observer is told it is complete.
     */
    public Detail observeeSign(int observationId, String comments, AccountPrincipal principal) {
        Row o = lock(observationId);
        if (!isObservee(o, principal))
            throw new AccessDeniedException("Only the observee can sign off on this observation.");
        if (o.status() == Status.COMPLETED)
            throw new ConflictException("This observation has already been signed off.");
        if (o.status() != Status.SUBMITTED)
            throw new ConflictException("The observer has not submitted this observation yet.");
        String text = comments == null || comments.isBlank() ? null : comments.strip();
        if (text != null && text.length() > MAX_COMMENTS)
            throw new IllegalArgumentException("Comments must be at most " + MAX_COMMENTS + " characters.");
        LocalDateTime signedAt = now();
        observations.updateObserveeSignedOff(Status.COMPLETED.name(), signedAt, signedAt, text, observationId);
        if (text != null) log(observationId, AuditEvent.OBSERVEE_COMMENTED, principal, null);
        log(observationId, AuditEvent.OBSERVEE_SIGNED, principal, null);
        log(observationId, AuditEvent.LOCKED, principal, null);
        signups.findById(o.signupId())
                .flatMap(signup -> observees.findById(signup.observeeId()))
                .ifPresent(observee -> observees.save(
                        new Observee(observee.observeeId(), observee.professorId(), observee.cycleId(), "COMPLETED")));
        notify(
                o.observer().professorId(),
                o.signupId(),
                o.observee().name() + " signed off on the observation of "
                        + o.course().label() + ". It is complete now.");
        return detail(find(observationId), principal);
    }

    /**
     * An Assessment Committee member forwards the link to a locked (completed) record in their school to another
     * professor. The forward is logged (who sent it, to whom, when) and the recipient is notified. Forwarding does not
     * give the recipient access: the link opens the secured system, which still decides who may read the record.
     */
    public void forward(int observationId, int recipientProfessorId, AccountPrincipal principal) {
        if (!principal.isCommitteeMember() || principal.user().professorId() == null)
            throw new AccessDeniedException("Only Assessment Committee members can forward a record.");
        Row o = find(observationId);
        if (!o.locked()) throw new ConflictException("Only a locked record can be forwarded.");
        if (!committeeMayRead(o, principal))
            throw new AccessDeniedException("Only committee members can forward completed records.");
        if (!professors.existsById(recipientProfessorId)) throw new NotFoundException("Professor not found.");
        log(observationId, AuditEvent.FORWARDED, principal, recipientProfessorId);
        notify(
                recipientProfessorId,
                o.signupId(),
                principal.user().name() + " sent you the observation of "
                        + o.course().label() + ".");
    }

    // Appends to the audit log. This is the only code that writes to the table, and it only inserts.
    private void log(int observationId, AuditEvent event, AccountPrincipal principal, Integer recipientProfessorId) {
        auditLog.append(observationId, event.name(), principal.user().professorId(), recipientProfessorId, now());
    }

    // Only the observer edits the answers, and only between starting the form and submitting it. The observee never
    // edits them: they can only comment and sign off.
    private static void requireEditable(Row o, AccountPrincipal principal) {
        if (!isObserver(o, principal)) throw new AccessDeniedException("Only the observer can edit this observation.");
        if (o.status() == Status.ACCEPTED || o.status() == Status.SCHEDULED)
            throw new ConflictException("Start the observation before recording answers.");
        if (o.status() != Status.IN_PROGRESS)
            throw new ConflictException("This observation has already been submitted.");
    }

    /**
     * The answers as they will be stored, after checking all of them so that nothing is saved if one is wrong. Each
     * answers a different question on the observation's own form, ratings fit the question's range and only rating
     * questions have one, and comments are trimmed, null when blank and at most 4000 characters.
     */
    private static List<Answer> checked(List<Answer> answers, Template template) {
        if (answers == null) throw new IllegalArgumentException("Send the form's responses.");
        Map<Integer, Criterion> questions = new HashMap<>();
        template.criteria().forEach(q -> questions.put(q.criterionId(), q));
        Set<Integer> seen = new HashSet<>();
        List<Answer> checked = new ArrayList<>();
        for (Answer a : answers) {
            if (a == null) throw new IllegalArgumentException("Each response must name a question.");
            Criterion q = questions.get(a.criterionId());
            if (q == null) throw new IllegalArgumentException("Question " + a.criterionId() + " is not on this form.");
            if (!seen.add(q.criterionId()))
                throw new IllegalArgumentException("\"" + q.criterionText() + "\" is answered more than once.");
            if (a.rating() != null && !"RATING".equals(q.questionType()))
                throw new IllegalArgumentException("\"" + q.criterionText() + "\" takes comments, not a rating.");
            if (a.rating() != null
                    && (q.ratingMin() != null && a.rating() < q.ratingMin()
                            || q.ratingMax() != null && a.rating() > q.ratingMax()))
                throw new IllegalArgumentException(
                        "Rate \"" + q.criterionText() + "\" from " + q.ratingMin() + " to " + q.ratingMax() + ".");
            String comments = a.comments() == null || a.comments().isBlank()
                    ? null
                    : a.comments().strip();
            if (comments != null && comments.length() > MAX_COMMENTS)
                throw new IllegalArgumentException(
                        "Comments on \"" + q.criterionText() + "\" must be at most " + MAX_COMMENTS + " characters.");
            checked.add(new Answer(q.criterionId(), a.rating(), comments));
        }
        return checked;
    }

    // A rating question needs a rating; a text question needs some text.
    private static boolean answered(Criterion q, Answer a) {
        if (a == null) return false;
        return "RATING".equals(q.questionType()) ? a.rating() != null : a.comments() != null;
    }

    // Replaces every stored answer. A question left with neither a rating nor comments is simply not stored.
    private void store(int observationId, List<Answer> answers) {
        observationResponses.deleteAnswersByObservationId(observationId);
        observationResponses.saveAll(answers.stream()
                .filter(a -> a.rating() != null || a.comments() != null)
                .map(a -> new ObservationResponse(null, observationId, a.criterionId(), a.rating(), a.comments()))
                .toList());
    }

    // Submission times are shown to the second, so they are stored that way.
    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    private void notify(int professorId, int signupId, String message) {
        notifications.save(new ObserverNotification(null, professorId, signupId, message, now()));
    }

    /**
     * Every observation the professor made or received, in every cycle, with the role they had in it. A received one
     * is listed before it is submitted, though the observee can only open it afterwards.
     */
    @Transactional(readOnly = true)
    public List<Summary> professorObservations(int professorId) {
        return observations.findDetailsByProfessorId(professorId).stream()
                .map(ObservationService::row)
                .map(o -> summary(
                        o, o.observer().professorId() == professorId ? ViewerRole.OBSERVER : ViewerRole.OBSERVEE))
                .toList();
    }

    private static Summary summary(Row o, ViewerRole role) {
        return new Summary(
                o.observationId(),
                role,
                o.status(),
                o.observee().name(),
                o.observer().name(),
                o.course().label(),
                o.semesterLabel(),
                o.cycleId(),
                o.observationDate(),
                o.startTime(),
                o.submittedAt());
    }
}
