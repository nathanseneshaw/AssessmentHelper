package edu.utdallas.cs4485.assessmenthelper.service;

import static java.util.Comparator.naturalOrder;

import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationPolicy;
import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final Clock clock;

    public EvaluationDeadlineService(
            ProfessorRepository professorRepository,
            EvaluationPolicyService policyService,
            EvaluationRecordService recordService,
            Clock clock) {
        this.professorRepository = professorRepository;
        this.policyService = policyService;
        this.recordService = recordService;
        this.clock = clock;
    }

    /**
     * The next due date is the last completed evaluation plus the interval for the professor's rank. A professor who
     * has never completed an evaluation is due one interval after their hire date. If there is no hire date either, the
     * due date is null and the professor is not overdue.
     *
     * @throws NotFoundException if the professor does not exist, has no rank, or the rank has no policy
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

        LocalDate lastEvaluation = recordService.findByProfessor(professorId).stream()
                .map(EvaluationRecord::lastObservationCompletedAt)
                .filter(Objects::nonNull)
                .max(naturalOrder())
                .map(completedAt -> completedAt.toLocalDate())
                .orElse(null);

        LocalDate start = lastEvaluation != null ? lastEvaluation : professor.hireDate();
        LocalDate nextDue = start == null ? null : start.plusYears(policy.evaluationIntervalYears());
        boolean overdue = nextDue != null && nextDue.isBefore(LocalDate.now(clock));

        return new EvaluationDeadline(
                professorId,
                professor.professorRank(),
                policy.evaluationIntervalYears(),
                lastEvaluation,
                nextDue,
                overdue);
    }
}
