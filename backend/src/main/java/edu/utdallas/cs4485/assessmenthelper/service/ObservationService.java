package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationTemplateService.Criterion;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationTemplateService.Template;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Observation forms as the observer fills them in and the observee reads them. The observer sees a form in every
 * status: they start it, save drafts, submit it, and can reopen it read-only afterwards. The observee may read it once
 * it is submitted, and nobody else may see it. Each of these returns the same {@link Detail}. An observation row is
 * created when the observer accepts a request, in {@link ObserverEligibilityService#respond}.
 */
@Service
@Transactional
public class ObservationService {
    private static final int MAX_COMMENTS = 4000;

    private final JdbcTemplate db;
    private final ObservationTemplateService templates;
    private final Clock clock;

    public ObservationService(JdbcTemplate db, ObservationTemplateService templates, Clock clock) {
        this.db = db;
        this.templates = templates;
        this.clock = clock;
    }

    /**
     * ACCEPTED or SCHEDULED, then IN_PROGRESS once started and SUBMITTED once the observer submits, when the observee
     * may read it too. COMPLETED marks records from earlier cycles, which both of them can still open.
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
        OBSERVEE
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
            boolean canEdit) {}

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
            LocalDateTime submittedAt) {}

    // The observer_signed_at column records when the observer submitted the form.
    private static final String OBSERVATIONS = """
            SELECT o.observation_id, o.signup_id, o.status, o.template_id, o.observation_date,
            o.start_time AS observation_start, o.observer_signed_at, oe.cycle_id, sm.term, sm.academic_year,
            pe.professor_id AS observee_professor_id, pe.first_name || ' ' || pe.last_name AS observee_name,
            de.department_name AS observee_department,
            po.professor_id AS observer_professor_id, po.first_name || ' ' || po.last_name AS observer_name,
            dp.department_name AS observer_department, s.section_id, s.section_number, s.meeting_days,
            s.start_time AS section_start, s.end_time AS section_end, s.location,
            c.course_code, c.course_number, c.course_name
            FROM observation o
            JOIN observer ob ON ob.observer_id=o.observer_id JOIN professor po ON po.professor_id=ob.professor_id
            JOIN department dp ON dp.department_id=po.department_id
            JOIN evaluation_signup es ON es.signup_id=o.signup_id JOIN observee oe ON oe.observee_id=es.observee_id
            JOIN professor pe ON pe.professor_id=oe.professor_id JOIN department de ON de.department_id=pe.department_id
            JOIN course_section s ON s.section_id=es.section_id JOIN course c ON c.course_id=s.course_id
            JOIN semester sm ON sm.semester_id=s.semester_id
            """;

    // Observations not yet dated (just accepted) come first, then the most recent.
    private static final String NEWEST_FIRST = " ORDER BY o.observation_date DESC NULLS FIRST, o.observation_id DESC";

    private List<Row> rows(String where, Object... args) {
        return db.query(OBSERVATIONS + where + NEWEST_FIRST, (r, n) -> row(r), args);
    }

    private static Row row(ResultSet r) throws SQLException {
        return new Row(
                r.getInt("observation_id"),
                r.getInt("signup_id"),
                Status.valueOf(r.getString("status")),
                r.getInt("template_id"),
                r.getInt("cycle_id"),
                r.getString("term") + " " + r.getInt("academic_year"),
                new Person(
                        r.getInt("observee_professor_id"),
                        r.getString("observee_name"),
                        r.getString("observee_department")),
                new Person(
                        r.getInt("observer_professor_id"),
                        r.getString("observer_name"),
                        r.getString("observer_department")),
                new Course(
                        r.getInt("section_id"),
                        r.getString("course_code") + " " + r.getString("course_number") + "."
                                + r.getString("section_number"),
                        r.getString("course_name"),
                        r.getString("meeting_days"),
                        r.getObject("section_start", LocalTime.class),
                        r.getObject("section_end", LocalTime.class),
                        r.getString("location")),
                r.getObject("observation_date", LocalDate.class),
                r.getObject("observation_start", LocalTime.class),
                r.getObject("observer_signed_at", LocalDateTime.class));
    }

    private Row find(int observationId) {
        return rows("WHERE o.observation_id=?", observationId).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Observation not found."));
    }

    // Writes call this first, so double submits and a save racing a submit run one after the other.
    private Row lock(int observationId) {
        if (db.queryForList(
                        "SELECT observation_id FROM observation WHERE observation_id=? FOR UPDATE",
                        Integer.class,
                        observationId)
                .isEmpty()) throw new NotFoundException("Observation not found.");
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
     * and the observee once the observer has submitted, so never a draft. Nobody else may, committee members included.
     */
    private static ViewerRole viewerRole(Row o, AccountPrincipal principal) {
        if (isObserver(o, principal)) return ViewerRole.OBSERVER;
        if (isObservee(o, principal) && (o.status() == Status.SUBMITTED || o.status() == Status.COMPLETED))
            return ViewerRole.OBSERVEE;
        return null;
    }

    @Transactional(readOnly = true)
    public Detail detail(int observationId, AccountPrincipal principal) {
        return detail(find(observationId), principal);
    }

    /** The form as {@code principal} sees it, with what they may do next. Only the observer ever changes it. */
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
                role == ViewerRole.OBSERVER && o.status() == Status.IN_PROGRESS);
    }

    private List<Answer> responses(int observationId) {
        return db.query(
                """
                SELECT r.criterion_id, r.rating, r.comments FROM observation_response r
                JOIN observation_criterion c ON c.criterion_id=r.criterion_id
                WHERE r.observation_id=? ORDER BY c.display_order, c.criterion_id, r.response_id
                """, (r, n) -> new Answer(r.getInt(1), r.getObject(2, Integer.class), r.getString(3)), observationId);
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
            db.update(
                    "UPDATE observation SET status=?, observation_date=?, start_time=? WHERE observation_id=?",
                    Status.IN_PROGRESS.name(),
                    early ? now.toLocalDate() : o.observationDate(),
                    early || o.startTime() == null ? minute : o.startTime(),
                    observationId);
        } else if (o.status() != Status.IN_PROGRESS)
            throw new ConflictException("This observation has already been submitted.");
        return detail(find(observationId), principal);
    }

    /** Saves the observer's draft. The answers sent replace every stored answer, and may leave questions blank. */
    public Detail saveResponses(int observationId, List<Answer> answers, AccountPrincipal principal) {
        Row o = lock(observationId);
        requireEditable(o, principal);
        store(observationId, checked(answers, templates.template(o.templateId())));
        return detail(find(observationId), principal);
    }

    /**
     * Saves the final answers and submits the form, once every required question is answered. From then on the
     * observer and the observee can both read it, and nobody can change it. The observee is told it is ready.
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
        db.update(
                "UPDATE observation SET status=?, observer_signed=TRUE, observer_signed_at=? WHERE observation_id=?",
                Status.SUBMITTED.name(),
                now(),
                observationId);
        notify(
                o.observee().professorId(),
                o.signupId(),
                o.observer().name() + " submitted the observation of "
                        + o.course().label() + ". You can view it now.");
        return detail(find(observationId), principal);
    }

    // Only the observer edits the answers, and only between starting the form and submitting it.
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
        db.update("DELETE FROM observation_response WHERE observation_id=?", observationId);
        db.batchUpdate(
                "INSERT INTO observation_response(observation_id,criterion_id,rating,comments) VALUES (?,?,?,?)",
                answers.stream()
                        .filter(a -> a.rating() != null || a.comments() != null)
                        .map(a -> new Object[] {observationId, a.criterionId(), a.rating(), a.comments()})
                        .toList());
    }

    // Submission times are shown to the second, so they are stored that way.
    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    private void notify(int professorId, int signupId, String message) {
        db.update(
                "INSERT INTO observer_notification(professor_id,signup_id,message,created_at) VALUES (?,?,?,?)",
                professorId,
                signupId,
                message,
                now());
    }

    /**
     * Every observation the professor made or received, in every cycle, with the role they had in it. A received one
     * is listed before it is submitted, though the observee can only open it afterwards.
     */
    @Transactional(readOnly = true)
    public List<Summary> professorObservations(int professorId) {
        return rows("WHERE po.professor_id=? OR pe.professor_id=?", professorId, professorId).stream()
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
