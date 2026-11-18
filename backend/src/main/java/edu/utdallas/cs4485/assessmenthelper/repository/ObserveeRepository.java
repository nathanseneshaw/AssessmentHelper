package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.Observee;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface ObserveeRepository extends ListCrudRepository<Observee, Integer> {
    List<Observee> findByProfessorId(Integer professorId);

    List<Observee> findByCycleId(Integer cycleId);

    public record SurveyCompletionCounts(Integer offered, Integer submitted) {}

    /**
     * offered = professors with an active (non-cancelled, non-deferred) signup; submitted = those who also had an
     * observation completed and submitted the survey, i.e. went through the whole process.
     */
    @Query("""
            SELECT COUNT(DISTINCT oe.professor_id) AS offered,
            COUNT(DISTINCT CASE WHEN f.professor_id IS NOT NULL AND EXISTS (
                SELECT 1 FROM observation o WHERE o.signup_id=es.signup_id AND UPPER(o.status)='COMPLETED')
                THEN oe.professor_id END) AS submitted
            FROM observee oe
            JOIN evaluation_signup es ON es.observee_id=oe.observee_id AND es.status NOT IN ('CANCELLED', 'DEFERRED')
            LEFT JOIN process_feedback f ON f.cycle_id=oe.cycle_id AND f.professor_id=oe.professor_id
            WHERE oe.cycle_id=:cycleId AND oe.status IN ('ACTIVE', 'COMPLETED')
            """)
    SurveyCompletionCounts countSurveyCompletion(Integer cycleId);

    @Modifying
    @Query(
            "UPDATE observee SET status='DEFERRED' WHERE observee_id=(SELECT observee_id FROM evaluation_signup WHERE signup_id=:signupId)")
    void markDeferredBySignupId(Integer signupId);
}
