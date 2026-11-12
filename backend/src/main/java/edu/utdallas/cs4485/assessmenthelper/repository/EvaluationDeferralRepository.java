package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationDeferral;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface EvaluationDeferralRepository extends ListCrudRepository<EvaluationDeferral, Integer> {
    List<EvaluationDeferral> findByProfessorId(Integer professorId);

    record EvaluationRecordDeferralRow(
            int cycleId, String reason, LocalDateTime deferredAt, String actor, String target) {}

    @Query("""
            SELECT oe.cycle_id,d.reason,d.deferred_at,p.first_name || ' ' || p.last_name AS actor,
            s.term || ' ' || s.academic_year AS target
            FROM evaluation_deferral d JOIN evaluation_signup es ON es.signup_id=d.signup_id
            JOIN observee oe ON oe.observee_id=es.observee_id JOIN professor p ON p.professor_id=d.deferred_by
            JOIN semester s ON s.semester_id=d.target_semester_id WHERE d.professor_id=:professorId
            """)
    List<EvaluationRecordDeferralRow> findRecordDeferralsByProfessorId(Integer professorId);

    record EligibilityDeferralRow(String reason, LocalDateTime deferredAt, String actor, String target) {}

    @Query("""
            SELECT d.reason,d.deferred_at,p.first_name || ' ' || p.last_name AS actor,
            s.term || ' ' || s.academic_year AS target FROM evaluation_deferral d
            JOIN professor p ON p.professor_id=d.deferred_by JOIN semester s ON s.semester_id=d.target_semester_id
            WHERE d.signup_id=:signupId
            """)
    Optional<EligibilityDeferralRow> findEligibilityDeferral(Integer signupId);

    @Modifying
    @Query(
            "INSERT INTO evaluation_deferral(signup_id,professor_id,target_semester_id,reason,deferred_by,deferred_at) VALUES (:signupId,:professorId,:targetSemesterId,:reason,:deferredBy,:deferredAt)")
    void insertDeferral(
            Integer signupId,
            Integer professorId,
            Integer targetSemesterId,
            String reason,
            Integer deferredBy,
            LocalDateTime deferredAt);
}
