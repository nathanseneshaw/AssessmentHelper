package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProcessFeedbackService {
    public record Survey(
            int cycleId,
            String label,
            LocalDate observationDeadline,
            LocalDate feedbackDeadline,
            String status,
            List<Integer> ratings,
            String comments,
            LocalDate submittedDate) {}
    /** Ratings follow the six questions in the displayed survey, ending with overall effectiveness. */
    public record Submission(List<Integer> ratings, String comments) {}

    private final JdbcTemplate db;
    private final Clock clock;

    public ProcessFeedbackService(JdbcTemplate db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    private int professorId(AccountPrincipal principal) {
        if (principal == null || principal.user().professorId() == null)
            throw new AccessDeniedException("A faculty profile is required.");
        return principal.user().professorId();
    }

    @Transactional(readOnly = true)
    public List<Survey> surveys(AccountPrincipal principal) {
        int id = professorId(principal);
        LocalDate today = LocalDate.now(clock);
        return db.query(
                """
                SELECT c.cycle_id, s.term, s.academic_year, c.observation_deadline, c.feedback_deadline,
                    f.feedback_id, f.clarity_rating, f.scheduling_rating, f.communication_rating,
                    f.organization_rating, f.usefulness_rating, f.overall_rating,
                    f.observee_comment, f.submitted_date FROM evaluation_cycle c
                JOIN semester s ON s.semester_id=c.semester_id
                JOIN observee oe ON oe.cycle_id=c.cycle_id AND oe.professor_id=?
                LEFT JOIN process_feedback f ON f.cycle_id=c.cycle_id AND f.professor_id=oe.professor_id
                WHERE oe.status IN ('ACTIVE', 'COMPLETED')
                ORDER BY s.academic_year DESC, s.start_date DESC, c.cycle_id DESC
                """,
                (r, row) -> {
                    LocalDate observation = r.getObject("observation_deadline", LocalDate.class);
                    LocalDate feedback = r.getObject("feedback_deadline", LocalDate.class);
                    String status;
                    if (r.getObject("feedback_id") != null) status = "SUBMITTED";
                    else if (observation == null || feedback == null || !feedback.isAfter(observation))
                        status = "UNCONFIGURED";
                    else if (!today.isAfter(observation)) status = "NOT_OPEN";
                    else if (today.isAfter(feedback)) status = "CLOSED";
                    else status = "AVAILABLE";
                    return new Survey(
                            r.getInt("cycle_id"),
                            r.getString("term") + " " + r.getInt("academic_year"),
                            observation,
                            feedback,
                            status,
                            r.getObject("feedback_id") == null
                                    ? null
                                    : java.util.Arrays.asList(
                                            r.getObject("clarity_rating", Integer.class),
                                            r.getObject("scheduling_rating", Integer.class),
                                            r.getObject("communication_rating", Integer.class),
                                            r.getObject("organization_rating", Integer.class),
                                            r.getObject("usefulness_rating", Integer.class),
                                            r.getObject("overall_rating", Integer.class)),
                            r.getString("observee_comment"),
                            r.getObject("submitted_date", LocalDate.class));
                },
                id);
    }

    public Survey submit(int cycleId, Submission body, AccountPrincipal principal) {
        int id = professorId(principal);
        // Serialize simultaneous submissions for the same faculty member, including requests from other tabs.
        db.queryForObject("SELECT professor_id FROM professor WHERE professor_id=? FOR UPDATE", Integer.class, id);
        Survey survey = surveys(principal).stream()
                .filter(s -> s.cycleId() == cycleId)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("You are not an observee in this evaluation cycle."));
        if (!survey.status().equals("AVAILABLE"))
            throw new ConflictException(
                    "This feedback survey is not available for submission (" + survey.status() + ").");
        if (body == null
                || body.ratings() == null
                || body.ratings().size() != 6
                || body.ratings().stream().anyMatch(r -> r == null || r < 1 || r > 5))
            throw new IllegalArgumentException("Answer all six questions with a rating from 1 to 5.");
        String comments = body.comments() == null ? null : body.comments().trim();
        if (comments != null && comments.length() > 4000)
            throw new IllegalArgumentException("Comments must be at most 4000 characters.");
        var ratings = body.ratings();
        db.update(
                """
                INSERT INTO process_feedback (cycle_id, professor_id, clarity_rating, scheduling_rating,
                    communication_rating, organization_rating, usefulness_rating, overall_rating,
                    observee_comment, submitted_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                cycleId,
                id,
                ratings.get(0),
                ratings.get(1),
                ratings.get(2),
                ratings.get(3),
                ratings.get(4),
                ratings.get(5),
                comments,
                LocalDate.now(clock));
        return surveys(principal).stream()
                .filter(s -> s.cycleId() == cycleId)
                .findFirst()
                .orElseThrow();
    }
}
