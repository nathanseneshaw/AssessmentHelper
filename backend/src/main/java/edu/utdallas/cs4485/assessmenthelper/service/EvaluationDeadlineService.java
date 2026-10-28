package edu.utdallas.cs4485.assessmenthelper.service;

import static java.util.Comparator.naturalOrder;

import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationPolicy;
import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.SemesterRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Rank-based evaluation deadlines. The interval for each rank comes from the evaluation_policy table (assistant
 * professors every year, associate and full professors every two years in the seed data), so changing a policy row
 * changes the deadline with no code change.
 */
@Service
public class EvaluationDeadlineService {

    private final ProfessorRepository professorRepository;
    private final EvaluationPolicyService policyService;
    private final EvaluationRecordService recordService;
    private final SemesterRepository semesterRepository;
    private final Clock clock;

    public EvaluationDeadlineService(
            ProfessorRepository professorRepository,
            EvaluationPolicyService policyService,
            EvaluationRecordService recordService,
            SemesterRepository semesterRepository,
            Clock clock) {
        this.professorRepository = professorRepository;
        this.policyService = policyService;
        this.recordService = recordService;
        this.semesterRepository = semesterRepository;
        this.clock = clock;
    }

    /**
     * Applies the same rules as the AC dashboard (see {@link EvaluationDueRules}). The last evaluation is the latest
     * cycle whose observee status is COMPLETED. A professor who has never completed one is anchored on the hire date;
     * with no hire date either, the due date is null and the professor is not overdue. Overdue is judged against the
     * current semester, and inactive professors or policies are never overdue.
     *
     * @throws NotFoundException if the professor does not exist, has no rank, or the rank has no policy
     * @throws ResponseStatusException (409) if no unique semester contains today's date
     */
    @Transactional(readOnly = true)
    public EvaluationDeadline findByProfessor(Integer professorId) {
        Professor professor = professorRepository
                .findById(professorId)
                .orElseThrow(() -> new NotFoundException("No professor with id " + professorId));
        if (professor.professorRank() == null) {
            throw new NotFoundException("Professor " + professorId + " has no rank");
        }
        EvaluationPolicy policy = policyService.findByRank(professor.professorRank());
        LocalDate today = LocalDate.now(clock);

        LocalDate lastEvaluation = recordService.findByProfessor(professorId).stream()
                .filter(r -> EvaluationDueRules.countsAsCompleted(r.status(), r.lastObservationCompletedAt(), today))
                .map(EvaluationRecord::lastObservationCompletedAt)
                .max(naturalOrder())
                .map(LocalDateTime::toLocalDate)
                .orElse(null);

        LocalDate anchor = lastEvaluation != null ? lastEvaluation : professor.hireDate();
        LocalDate nextDue = null;
        boolean overdue = false;
        if (anchor != null) {
            var current = semesterRepository.findAll().stream()
                    .filter(s -> s.startDate() != null
                            && s.endDate() != null
                            && !today.isBefore(s.startDate())
                            && !today.isAfter(s.endDate()))
                    .toList();
            if (current.size() != 1) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "No unique semester contains today's date. Configure semester dates first.");
            }
            var result = EvaluationDueRules.calculate(anchor, policy.evaluationIntervalYears(), current.getFirst());
            nextDue = result.nextDue();
            overdue = result.overdue() && EvaluationDueRules.applies(professor, policy);
        }

        return new EvaluationDeadline(
                professorId,
                professor.professorRank(),
                policy.evaluationIntervalYears(),
                lastEvaluation,
                nextDue,
                overdue);
    }
}
