package edu.utdallas.cs4485.assessmenthelper.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persisted recommendations, committee release, observer responses and in-app notifications. Signups are saved by
 * {@link EvaluationSignupService} and requests by {@link ObserverRequestService}; both call in here for signups that
 * have an observation window, which are the ones with a generated candidate list.
 */
@Service
@Transactional
public class ObserverEligibilityService {
    private final JdbcTemplate db;
    private final Clock clock;

    public ObserverEligibilityService(JdbcTemplate db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    public boolean manages(int signupId) {
        return db.queryForObject("SELECT COUNT(*) FROM observer_list_state WHERE signup_id=?", Integer.class, signupId)
                > 0;
    }

    public void confirmExistingRequest(int requestId) {
        int professorId = db.queryForObject(
                "SELECT ob.professor_id FROM observer_request r JOIN observer ob ON ob.observer_id=r.observer_id WHERE r.request_id=?",
                Integer.class,
                requestId);
        respond(professorId, requestId, true);
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
            String status) {}

    public record Notification(int notificationId, String message, OffsetDateTime createdAt) {}

    public record FacultyState(
            Cycle cycle,
            Person professor,
            Signup signup,
            List<Request> incoming,
            List<Request> outgoing,
            List<Notification> notifications) {}

    public record AcState(Cycle cycle, List<Signup> signups) {}

    /** The observation window a signup may choose: from {@code start} through any date up to {@code latestEnd}. */
    public record Window(
            LocalDate start, LocalDate latestEnd, LocalDate end, LocalDate signupDeadline, boolean signupOpen) {}

    private static final String CYCLES = """
            SELECT c.*, s.term, s.academic_year, s.start_date FROM evaluation_cycle c
            JOIN semester s ON s.semester_id=c.semester_id
            """;

    private List<Cycle> cycles(String where, Object... args) {
        LocalDate today = LocalDate.now(clock);
        return db.query(
                CYCLES + where,
                (r, n) -> new Cycle(
                        r.getInt("cycle_id"),
                        r.getInt("semester_id"),
                        r.getString("term") + " " + r.getInt("academic_year"),
                        date(r, "start_date"),
                        date(r, "signup_deadline"),
                        date(r, "observation_deadline"),
                        date(r, "feedback_deadline"),
                        "OPEN".equals(r.getString("status"))
                                && date(r, "signup_deadline") != null
                                && !today.isAfter(date(r, "signup_deadline")),
                        "OPEN".equals(r.getString("status"))
                                && date(r, "observation_deadline") != null
                                && !today.isAfter(date(r, "observation_deadline")),
                        "OPEN".equals(r.getString("status"))
                                && date(r, "signup_deadline") != null
                                && today.isAfter(date(r, "signup_deadline"))
                                && date(r, "observation_deadline") != null
                                && !today.isAfter(date(r, "observation_deadline"))),
                args);
    }

    /** The cycle of the semester that contains today, which the faculty and committee screens show. */
    public Cycle cycle() {
        LocalDate today = LocalDate.now(clock);
        var rows = cycles("WHERE s.start_date<=? AND s.end_date>=?", today, today);
        if (rows.size() != 1)
            throw new ConflictException("Configure exactly one current semester and evaluation cycle.");
        return rows.getFirst();
    }

    private Cycle cycle(int cycleId) {
        return cycles("WHERE c.cycle_id=?", cycleId).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Evaluation cycle not found."));
    }

    private int cycleIdOf(int signupId) {
        return db
                .queryForList(
                        "SELECT oe.cycle_id FROM evaluation_signup es JOIN observee oe ON oe.observee_id=es.observee_id WHERE es.signup_id=?",
                        Integer.class,
                        signupId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Signup not found."));
    }

    // Serializes pool changes and request transitions, including concurrent duplicate submissions.
    private Cycle lockCycle(int cycleId) {
        db.queryForObject("SELECT cycle_id FROM evaluation_cycle WHERE cycle_id=? FOR UPDATE", Integer.class, cycleId);
        return cycle(cycleId);
    }

    private Person person(int id) {
        return db.query("""
                SELECT p.professor_id, p.first_name || ' ' || p.last_name AS name, d.department_name
                FROM professor p JOIN department d ON d.department_id=p.department_id
                WHERE p.active_status=TRUE AND p.professor_id=?
                """, (r, n) -> new Person(r.getInt(1), r.getString(2), r.getString(3)), id).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Active professor not found."));
    }

    private List<Section> sections(int id, Cycle c) {
        return db.query("""
                SELECT DISTINCT s.*, c.course_code, c.course_number, c.course_name
                FROM teaching_history t JOIN course_section s ON s.section_id=t.section_id
                JOIN course c ON c.course_id=s.course_id WHERE t.professor_id=? AND s.semester_id=?
                ORDER BY s.section_id
                """, (r, n) -> section(r), id, c.semesterId());
    }

    private static Section section(ResultSet r) throws SQLException {
        return new Section(
                r.getInt("section_id"),
                r.getString("course_code") + " " + r.getString("course_number") + "." + r.getString("section_number"),
                r.getString("course_name"),
                r.getString("meeting_days"),
                r.getObject("start_time", LocalTime.class),
                r.getObject("end_time", LocalTime.class));
    }

    private static LocalDate date(ResultSet r, String column) throws SQLException {
        return r.getObject(column, LocalDate.class);
    }

    private static final String SIGNUPS = """
            SELECT es.signup_id, p.professor_id, p.first_name || ' ' || p.last_name AS name,
            d.department_name, s.*, c.course_code, c.course_number, c.course_name,
            ls.window_start, ls.window_end, ls.released_at
            FROM evaluation_signup es JOIN observee oe ON oe.observee_id=es.observee_id
            JOIN professor p ON p.professor_id=oe.professor_id
            JOIN department d ON d.department_id=p.department_id
            JOIN course_section s ON s.section_id=es.section_id JOIN course c ON c.course_id=s.course_id
            JOIN observer_list_state ls ON ls.signup_id=es.signup_id
            WHERE oe.cycle_id=? AND es.status<>'CANCELLED' ORDER BY es.signup_id
            """;

    private List<Signup> signups(Cycle c, boolean includeCandidates) {
        return db.query(
                SIGNUPS,
                (r, n) -> new Signup(
                        r.getInt("signup_id"),
                        r.getInt("professor_id"),
                        r.getString("name"),
                        r.getString("department_name"),
                        section(r),
                        date(r, "window_start"),
                        date(r, "window_end"),
                        r.getTimestamp("released_at") != null,
                        includeCandidates ? candidates(r.getInt("signup_id")) : List.of()),
                c.cycleId());
    }

    private List<Candidate> candidates(int signupId) {
        return db.query(
                """
                SELECT ob.observer_id, p.professor_id, p.first_name || ' ' || p.last_name AS name,
                d.department_name, c.course_number FROM observer_match m
                JOIN observer ob ON ob.observer_id=m.observer_id JOIN professor p ON p.professor_id=ob.professor_id
                JOIN department d ON d.department_id=p.department_id
                JOIN evaluation_signup es ON es.signup_id=m.signup_id
                JOIN course_section s ON s.section_id=es.section_id JOIN course c ON c.course_id=s.course_id
                WHERE m.signup_id=? ORDER BY m.match_id
                """,
                (r, n) -> new Candidate(
                        r.getInt(1),
                        r.getInt(2),
                        r.getString(3),
                        r.getString(4),
                        level(r.getString(5)) + "-level",
                        qualifyingCourses(r.getInt(2), signupId)),
                signupId);
    }

    private List<String> qualifyingCourses(int professorId, int signupId) {
        return db
                .query(
                        """
                SELECT DISTINCT c.course_code, c.course_number, c.course_name, s.section_number,
                    target_course.course_number AS target_number
                FROM evaluation_signup es
                JOIN observee oe ON oe.observee_id=es.observee_id
                JOIN professor target_prof ON target_prof.professor_id=oe.professor_id
                JOIN course_section target_section ON target_section.section_id=es.section_id
                JOIN course target_course ON target_course.course_id=target_section.course_id
                JOIN teaching_history t ON t.professor_id=?
                JOIN course_section s ON s.section_id=t.section_id AND s.semester_id=target_section.semester_id
                JOIN course c ON c.course_id=s.course_id AND c.department_id=target_prof.department_id
                WHERE es.signup_id=? ORDER BY c.course_code, c.course_number, s.section_number
                """,
                        (r, n) -> level(r.getString("course_number")) == level(r.getString("target_number"))
                                ? r.getString("course_code") + " " + r.getString("course_number") + "."
                                        + r.getString("section_number") + " - " + r.getString("course_name")
                                : null,
                        professorId,
                        signupId)
                .stream()
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
                    List.of());
        var notifications = db.query(
                """
                SELECT n.* FROM observer_notification n JOIN observer_list_state ls ON ls.signup_id=n.signup_id
                JOIN evaluation_signup es ON es.signup_id=n.signup_id JOIN observee oe ON oe.observee_id=es.observee_id
                WHERE n.professor_id=? AND oe.cycle_id=? ORDER BY n.notification_id DESC
                """,
                (r, n) -> new Notification(
                        r.getInt("notification_id"),
                        r.getString("message"),
                        r.getObject("created_at", LocalDateTime.class)
                                .atZone(clock.getZone())
                                .toOffsetDateTime()),
                id,
                c.cycleId());
        List<Request> requests = requests(c);
        return new FacultyState(
                c,
                p,
                signup,
                requests.stream().filter(r -> r.professorId() == id).toList(),
                requests.stream().filter(r -> signupOwner(r.signupId()) == id).toList(),
                notifications);
    }

    public AcState acState() {
        Cycle c = cycle();
        return new AcState(c, signups(c, true));
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
        return db
                .query(
                        "SELECT window_end FROM observer_list_state WHERE signup_id=?",
                        (r, n) -> date(r, "window_end"),
                        signupId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * Checks the signup deadline and observation window for a signup that was just saved, then adds the professor to
     * the cycle's observer pool and fills candidate lists. A null end keeps the saved window, or for a new signup runs
     * through the observation deadline.
     */
    public void signedUp(int signupId, LocalDate end) {
        Cycle c = lockCycle(cycleIdOf(signupId));
        if (!c.signupOpen()) throw new ConflictException("The signup deadline has passed or this cycle is closed.");
        var signup = db.queryForMap(
                "SELECT oe.professor_id, es.section_id FROM evaluation_signup es JOIN observee oe ON oe.observee_id=es.observee_id WHERE es.signup_id=?",
                signupId);
        int professorId = ((Number) signup.get("PROFESSOR_ID")).intValue();
        Section section = db.queryForObject("""
                SELECT s.*, c.course_code, c.course_number, c.course_name
                FROM course_section s JOIN course c ON c.course_id=s.course_id WHERE s.section_id=?
                """, (r, n) -> section(r), signup.get("SECTION_ID"));
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
        if (saved == null)
            db.update(
                    "INSERT INTO observer_list_state(signup_id,window_start,window_end) VALUES (?,?,?)",
                    signupId,
                    start,
                    end);
        else
            db.update(
                    "UPDATE observer_list_state SET window_start=?,window_end=? WHERE signup_id=?",
                    start,
                    end,
                    signupId);
        var observers = db.queryForList(
                "SELECT observer_id FROM observer WHERE professor_id=? AND cycle_id=?",
                Integer.class,
                professorId,
                c.cycleId());
        if (observers.isEmpty())
            db.update(
                    "INSERT INTO observer(professor_id,cycle_id,active_status) VALUES (?,?,TRUE)",
                    professorId,
                    c.cycleId());
        else
            db.update(
                    "UPDATE observer SET active_status=TRUE WHERE professor_id=? AND cycle_id=?",
                    professorId,
                    c.cycleId());
        refresh(c);
    }

    /** Adds to incomplete lists without reshuffling existing candidates or changing released lists. */
    public AcState generate() {
        Cycle c = lockCycle(cycle().cycleId());
        refresh(c);
        return new AcState(c, signups(c, true));
    }

    private void refresh(Cycle c) {
        if (!c.requestsOpen()) return;
        for (Signup s : signups(c, true)) {
            if (s.released() || s.candidates().size() >= 5) continue;
            var eligible = new ArrayList<>(eligible(s, c));
            var existing = s.candidates().stream().map(Candidate::observerId).toList();
            eligible.removeAll(existing);
            Collections.shuffle(eligible);
            for (int observerId : eligible.stream().limit(5 - existing.size()).toList())
                db.update(
                        "INSERT INTO observer_match(signup_id,observer_id,generated_date,match_status) VALUES (?,?,?,'PROPOSED')",
                        s.signupId(),
                        observerId,
                        LocalDate.now(clock));
        }
    }

    static int level(String number) {
        if (number == null || !number.matches("[1-9][0-9]{3}")) return -1;
        return Integer.parseInt(number) / 1000 * 1000;
    }

    private List<Integer> eligible(Signup signup, Cycle c) {
        var target = db.queryForMap("""
                SELECT p.department_id, co.course_number FROM professor p, course_section s
                JOIN course co ON co.course_id=s.course_id WHERE p.professor_id=? AND s.section_id=?
                """, signup.professorId(), signup.section().sectionId());
        int targetLevel = level((String) target.get("COURSE_NUMBER"));
        if (targetLevel < 0) return List.of();
        var pool = db.queryForList("""
                SELECT DISTINCT ob.observer_id, p.professor_id FROM observer ob
                JOIN professor p ON p.professor_id=ob.professor_id
                JOIN observee oe ON oe.professor_id=p.professor_id AND oe.cycle_id=ob.cycle_id
                JOIN evaluation_signup es ON es.observee_id=oe.observee_id
                WHERE ob.cycle_id=? AND ob.active_status=TRUE AND p.active_status=TRUE
                AND p.department_id=? AND p.professor_id<>? AND es.status<>'CANCELLED'
                """, c.cycleId(), target.get("DEPARTMENT_ID"), signup.professorId());
        List<Integer> result = new ArrayList<>();
        for (var row : pool) {
            int id = ((Number) row.get("PROFESSOR_ID")).intValue();
            var assignments = sections(id, c);
            boolean teachesLevel =
                    db.queryForList("""
                    SELECT co.course_number FROM teaching_history t JOIN course_section s ON s.section_id=t.section_id
                    JOIN course co ON co.course_id=s.course_id WHERE t.professor_id=? AND s.semester_id=? AND co.department_id=?
                    """, String.class, id, c.semesterId(), target.get("DEPARTMENT_ID")).stream()
                            .anyMatch(n -> level(n) == targetLevel);
            if (!teachesLevel) continue;
            Set<DayOfWeek> blocked = EnumSet.noneOf(DayOfWeek.class);
            boolean unknown = false;
            for (Section taught : assignments) {
                if (!validSchedule(taught)) {
                    unknown = true;
                    break;
                }
                if (taught.startTime().isBefore(signup.section().endTime())
                        && signup.section().startTime().isBefore(taught.endTime()))
                    blocked.addAll(days(taught.meetingDays()));
            }
            if (!unknown
                    && hasMeeting(
                            signup.section(),
                            signup.windowStart().isBefore(LocalDate.now(clock))
                                    ? LocalDate.now(clock)
                                    : signup.windowStart(),
                            signup.windowEnd(),
                            blocked)) result.add(((Number) row.get("OBSERVER_ID")).intValue());
        }
        return result;
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
        Cycle c = lockCycle(cycleIdOf(signupId));
        if (!c.requestsOpen()) throw new ConflictException("The observation period is closed.");
        Signup s = managedSignup(signupId, c);
        if (s.released()) return;
        if (!c.candidateListsOpen())
            throw new ConflictException("Candidate lists can only be sent after the signup deadline ends.");
        if (s.candidates().isEmpty())
            throw new ConflictException("No eligible observers. The committee must resolve this signup.");
        var eligible = eligible(s, c);
        if (s.candidates().stream().anyMatch(p -> !eligible.contains(p.observerId())))
            throw new ConflictException(
                    "A candidate is no longer eligible. Review the teaching schedule before release.");
        db.update("UPDATE observer_list_state SET released_at=? WHERE signup_id=?", LocalDateTime.now(clock), signupId);
        notify(s.professorId(), signupId, "Your generated observer list is ready. Choose an observer to request.");
    }

    private Signup managedSignup(int id, Cycle c) {
        return signups(c, true).stream()
                .filter(s -> s.signupId() == id)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Signup not found."));
    }

    private int signupOwner(int id) {
        return db.queryForObject(
                "SELECT oe.professor_id FROM evaluation_signup es JOIN observee oe ON oe.observee_id=es.observee_id WHERE es.signup_id=?",
                Integer.class,
                id);
    }

    private boolean released(int signupId) {
        return db.queryForObject(
                        "SELECT COUNT(*) FROM observer_list_state WHERE signup_id=? AND released_at IS NOT NULL",
                        Integer.class,
                        signupId)
                > 0;
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
        int recipient =
                db.queryForObject("SELECT professor_id FROM observer WHERE observer_id=?", Integer.class, observerId);
        notify(
                recipient,
                signupId,
                s.name() + " requested an observation of " + s.section().label() + " ("
                        + s.section().meetingDays() + " " + s.section().startTime() + "-"
                        + s.section().endTime() + ").");
    }

    private List<Request> requests(Cycle c) {
        db.update("""
                UPDATE observer_request SET status='EXPIRED' WHERE status='PENDING' AND expiration_date<=?
                AND signup_id IN (SELECT es.signup_id FROM evaluation_signup es JOIN observee oe ON oe.observee_id=es.observee_id WHERE oe.cycle_id=?)
                """, LocalDateTime.now(clock), c.cycleId());
        return db.query(
                """
                SELECT r.request_id, r.signup_id, ob.professor_id, p.first_name || ' ' || p.last_name AS observee_name,
                op.first_name || ' ' || op.last_name AS observer_name, r.status, s.*, co.course_code, co.course_number,
                co.course_name, ls.window_start, ls.window_end FROM observer_request r
                JOIN observer ob ON ob.observer_id=r.observer_id JOIN professor op ON op.professor_id=ob.professor_id
                JOIN evaluation_signup es ON es.signup_id=r.signup_id JOIN observee oe ON oe.observee_id=es.observee_id
                JOIN professor p ON p.professor_id=oe.professor_id JOIN course_section s ON s.section_id=es.section_id
                JOIN course co ON co.course_id=s.course_id JOIN observer_list_state ls ON ls.signup_id=es.signup_id
                WHERE oe.cycle_id=? ORDER BY r.request_id DESC
                """,
                (r, n) -> new Request(
                        r.getInt("request_id"),
                        r.getInt("signup_id"),
                        r.getInt("professor_id"),
                        r.getString("observee_name"),
                        r.getString("observer_name"),
                        section(r),
                        date(r, "window_start"),
                        date(r, "window_end"),
                        r.getString("status")),
                c.cycleId());
    }

    /** Each acceptance records that observer's observation without changing other requests. */
    public void respond(int professorId, int requestId, boolean accept) {
        int signupId = db
                .queryForList("SELECT signup_id FROM observer_request WHERE request_id=?", Integer.class, requestId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Observation request not found."));
        Cycle c = lockCycle(cycleIdOf(signupId));
        Request r = requests(c).stream()
                .filter(x -> x.requestId() == requestId && x.professorId() == professorId)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Observation request not found."));
        if (!c.requestsOpen() || !"PENDING".equals(r.status()))
            throw new ConflictException("This request is no longer pending or the observation period is closed.");
        int observer = db.queryForObject(
                "SELECT observer_id FROM observer_request WHERE request_id=?", Integer.class, requestId);
        if (accept) {
            if (!eligible(managedSignup(signupId, c), c).contains(observer))
                throw new ConflictException("You are no longer eligible for this observation.");
            if (db.queryForObject(
                            "SELECT COUNT(*) FROM observation WHERE signup_id=? AND observer_id=?",
                            Integer.class,
                            signupId,
                            observer)
                    > 0) throw new ConflictException("This observer already has an observation for this signup.");
        }
        var templates = db.queryForList(
                "SELECT template_id FROM observation_template WHERE active_status=TRUE ORDER BY version DESC,template_id DESC",
                Integer.class);
        if (accept && templates.isEmpty())
            throw new ConflictException("Configure an active observation template first.");
        db.update(
                "UPDATE observer_request SET status=?,response_date=? WHERE request_id=?",
                accept ? "CONFIRMED" : "DECLINED",
                LocalDateTime.now(clock),
                requestId);
        if (accept)
            db.update(
                    "INSERT INTO observation(signup_id,observer_id,template_id,status) VALUES (?,?,?,'ACCEPTED')",
                    signupId,
                    observer,
                    templates.getFirst());
        notify(
                signupOwner(signupId),
                signupId,
                r.observerName() + (accept ? " accepted" : " declined") + " your observation request for "
                        + r.section().label() + ".");
    }

    private void notify(int professorId, int signupId, String message) {
        db.update(
                "INSERT INTO observer_notification(professor_id,signup_id,message,created_at) VALUES (?,?,?,?)",
                professorId,
                signupId,
                message,
                LocalDateTime.now(clock));
    }
}
