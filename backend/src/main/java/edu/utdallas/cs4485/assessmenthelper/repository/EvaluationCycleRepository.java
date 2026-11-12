package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationCycle;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;
import org.springframework.data.repository.ListCrudRepository;

public interface EvaluationCycleRepository extends ListCrudRepository<EvaluationCycle, Integer> {
    @Lock(LockMode.PESSIMISTIC_WRITE)
    Optional<EvaluationCycle> findForUpdateByCycleId(Integer cycleId);

    public record FeedbackSurveyRow(
            Integer cycleId,
            String term,
            Integer academicYear,
            LocalDate observationDeadline,
            LocalDate feedbackDeadline,
            Integer feedbackId,
            Integer clarityRating,
            Integer schedulingRating,
            Integer communicationRating,
            Integer organizationRating,
            Integer usefulnessRating,
            Integer overallRating,
            String observeeComment,
            LocalDate submittedDate) {}

    @Query("""
            SELECT c.cycle_id, s.term, s.academic_year, c.observation_deadline, c.feedback_deadline,
                f.feedback_id, f.clarity_rating, f.scheduling_rating, f.communication_rating,
                f.organization_rating, f.usefulness_rating, f.overall_rating,
                f.observee_comment, f.submitted_date FROM evaluation_cycle c
            JOIN semester s ON s.semester_id=c.semester_id
            JOIN observee oe ON oe.cycle_id=c.cycle_id AND oe.professor_id=:professorId
            LEFT JOIN process_feedback f ON f.cycle_id=c.cycle_id AND f.professor_id=oe.professor_id
            WHERE oe.status IN ('ACTIVE', 'COMPLETED')
            ORDER BY s.academic_year DESC, s.start_date DESC, c.cycle_id DESC
            """)
    List<FeedbackSurveyRow> findFeedbackSurveys(Integer professorId);

    record EligibilityCycleRow(
            Integer cycleId,
            Integer semesterId,
            LocalDate signupDeadline,
            LocalDate observationDeadline,
            LocalDate feedbackDeadline,
            String status,
            String term,
            Integer academicYear,
            LocalDate startDate) {}

    @Query("""
            SELECT c.*, s.term, s.academic_year, s.start_date FROM evaluation_cycle c
            JOIN semester s ON s.semester_id=c.semester_id
            WHERE s.start_date<=:today AND s.end_date>=:today
            """)
    List<EligibilityCycleRow> findEligibilityCyclesContaining(LocalDate today);

    @Query("""
            SELECT c.*, s.term, s.academic_year, s.start_date FROM evaluation_cycle c
            JOIN semester s ON s.semester_id=c.semester_id
            WHERE c.cycle_id=:cycleId
            """)
    Optional<EligibilityCycleRow> findEligibilityCycle(Integer cycleId);

    @Modifying
    @Query(
            "UPDATE evaluation_cycle SET signup_deadline=:signupDeadline, observation_deadline=:observationDeadline, feedback_deadline=:feedbackDeadline WHERE cycle_id=:cycleId")
    void updateDeadlines(
            Integer cycleId, LocalDate signupDeadline, LocalDate observationDeadline, LocalDate feedbackDeadline);
}
