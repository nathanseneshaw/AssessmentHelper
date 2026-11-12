package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.entity.ProcessFeedback;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationCycleRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProcessFeedbackRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
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

    private final EvaluationCycleRepository cycleRepository;
    private final ProfessorRepository professorRepository;
    private final ProcessFeedbackRepository feedbackRepository;
    private final Clock clock;

    public ProcessFeedbackService(
            EvaluationCycleRepository cycleRepository,
            ProfessorRepository professorRepository,
            ProcessFeedbackRepository feedbackRepository,
            Clock clock) {
        this.cycleRepository = cycleRepository;
        this.professorRepository = professorRepository;
        this.feedbackRepository = feedbackRepository;
        this.clock = clock;
    }

    public static String window(LocalDate observationDeadline, LocalDate feedbackDeadline, LocalDate today) {
        if (observationDeadline == null || feedbackDeadline == null || !feedbackDeadline.isAfter(observationDeadline))
            return "UNCONFIGURED";
        if (!today.isAfter(observationDeadline)) return "NOT_OPEN";
        if (today.isAfter(feedbackDeadline)) return "CLOSED";
        return "AVAILABLE";
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
        return cycleRepository.findFeedbackSurveys(id).stream()
                .map(r -> {
                    LocalDate observation = r.observationDeadline();
                    LocalDate feedback = r.feedbackDeadline();
                    String status = r.feedbackId() != null ? "SUBMITTED" : window(observation, feedback, today);
                    return new Survey(
                            r.cycleId(),
                            r.term() + " " + r.academicYear(),
                            observation,
                            feedback,
                            status,
                            r.feedbackId() == null
                                    ? null
                                    : java.util.Arrays.asList(
                                            r.clarityRating(),
                                            r.schedulingRating(),
                                            r.communicationRating(),
                                            r.organizationRating(),
                                            r.usefulnessRating(),
                                            r.overallRating()),
                            r.observeeComment(),
                            r.submittedDate());
                })
                .toList();
    }

    public Survey submit(int cycleId, Submission body, AccountPrincipal principal) {
        int id = professorId(principal);
        // Serialize simultaneous submissions for the same faculty member, including requests from other tabs.
        professorRepository.findForUpdateByProfessorId(id).orElseThrow();
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
        feedbackRepository.save(new ProcessFeedback(
                null,
                cycleId,
                id,
                null,
                ratings.get(5),
                null,
                null,
                comments,
                LocalDate.now(clock),
                ratings.get(0),
                ratings.get(1),
                ratings.get(2),
                ratings.get(3),
                ratings.get(4)));
        return surveys(principal).stream()
                .filter(s -> s.cycleId() == cycleId)
                .findFirst()
                .orElseThrow();
    }
}
