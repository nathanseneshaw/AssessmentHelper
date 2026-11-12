package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationSignup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

/** Read models for sending requests against existing, generated matches. */
public interface ObserverRequestContextRepository extends Repository<EvaluationSignup, Integer> {
    public record SignupContext(
            Integer signupId,
            Integer professorId,
            Integer cycleId,
            String courseLabel,
            String semesterLabel,
            String signupStatus,
            String observeeStatus,
            Boolean professorActive,
            String cycleStatus,
            Boolean observationExists) {}

    public record Candidate(Integer matchId, Integer observerId, String observerName, String department) {}

    String SIGNUPS = """
            SELECT s.signup_id, e.professor_id, e.cycle_id,
                   CONCAT(c.course_code, ' ', c.course_number, '.', cs.section_number) AS course_label,
                   CONCAT(sem.term, ' ', sem.academic_year) AS semester_label,
                   s.status AS signup_status, e.status AS observee_status,
                   p.active_status AS professor_active, ec.status AS cycle_status,
                   EXISTS(SELECT 1 FROM observation ob WHERE ob.signup_id = s.signup_id) AS observation_exists
            FROM evaluation_signup s
            JOIN observee e ON e.observee_id = s.observee_id
            JOIN professor p ON p.professor_id = e.professor_id
            JOIN evaluation_cycle ec ON ec.cycle_id = e.cycle_id
            JOIN semester sem ON sem.semester_id = ec.semester_id
            JOIN course_section cs ON cs.section_id = s.section_id
            JOIN course c ON c.course_id = cs.course_id
            """;

    @Query(SIGNUPS + " WHERE s.signup_id = :signupId")
    Optional<SignupContext> findSignup(Integer signupId);

    @Query(SIGNUPS + " WHERE e.professor_id = :professorId ORDER BY sem.start_date DESC, s.signup_id DESC")
    List<SignupContext> findSignups(Integer professorId);

    @Query("""
            SELECT m.match_id, m.observer_id, CONCAT(p.first_name, ' ', p.last_name) AS observer_name,
                   d.department_name AS department
            FROM observer_match m
            JOIN observer o ON o.observer_id = m.observer_id
            JOIN professor p ON p.professor_id = o.professor_id
            JOIN department d ON d.department_id = p.department_id
            JOIN evaluation_signup s ON s.signup_id = m.signup_id
            JOIN observee e ON e.observee_id = s.observee_id
            WHERE m.signup_id = :signupId AND m.match_status = 'PROPOSED'
                  AND o.active_status = TRUE AND p.active_status = TRUE
                  AND o.cycle_id = e.cycle_id AND o.professor_id <> e.professor_id
            ORDER BY m.match_id
            """)
    List<Candidate> candidates(Integer signupId);

    @Query("""
            SELECT CONCAT(p.first_name, ' ', p.last_name)
            FROM observer o JOIN professor p ON p.professor_id = o.professor_id WHERE o.observer_id = :observerId
            """)
    Optional<String> observerName(Integer observerId);

    @Query("""
            SELECT EXISTS(SELECT 1 FROM observer_match WHERE signup_id = :signupId AND match_status = 'PROPOSED')
                OR EXISTS(SELECT 1 FROM observer_request WHERE signup_id = :signupId)
                OR EXISTS(SELECT 1 FROM observation WHERE signup_id = :signupId)
            """)
    boolean hasStartedMatching(Integer signupId);
}
