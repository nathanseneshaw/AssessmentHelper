package edu.utdallas.cs4485.assessmenthelper.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read models for sending requests against existing, generated matches. */
@Repository
public class ObserverRequestContextRepository {
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

    private static final String SIGNUPS = """
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

    private final JdbcTemplate db;

    public ObserverRequestContextRepository(JdbcTemplate db) {
        this.db = db;
    }

    public Optional<SignupContext> findSignup(Integer signupId) {
        return db
                .query(
                        SIGNUPS + " WHERE s.signup_id = ?",
                        DataClassRowMapper.newInstance(SignupContext.class),
                        signupId)
                .stream()
                .findFirst();
    }

    public List<SignupContext> findSignups(Integer professorId) {
        return db.query(
                SIGNUPS + " WHERE e.professor_id = ? ORDER BY sem.start_date DESC, s.signup_id DESC",
                DataClassRowMapper.newInstance(SignupContext.class),
                professorId);
    }

    public List<Candidate> candidates(Integer signupId) {
        return db.query("""
                SELECT m.match_id, m.observer_id, CONCAT(p.first_name, ' ', p.last_name) AS observer_name,
                       d.department_name AS department
                FROM observer_match m
                JOIN observer o ON o.observer_id = m.observer_id
                JOIN professor p ON p.professor_id = o.professor_id
                JOIN department d ON d.department_id = p.department_id
                JOIN evaluation_signup s ON s.signup_id = m.signup_id
                JOIN observee e ON e.observee_id = s.observee_id
                WHERE m.signup_id = ? AND m.match_status = 'PROPOSED'
                      AND o.active_status = TRUE AND p.active_status = TRUE
                      AND o.cycle_id = e.cycle_id AND o.professor_id <> e.professor_id
                ORDER BY m.match_id
                """, DataClassRowMapper.newInstance(Candidate.class), signupId);
    }

    public String observerName(Integer observerId) {
        return db.queryForObject("""
                SELECT CONCAT(p.first_name, ' ', p.last_name)
                FROM observer o JOIN professor p ON p.professor_id = o.professor_id WHERE o.observer_id = ?
                """, String.class, observerId);
    }

    public boolean hasStartedMatching(Integer signupId) {
        return Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM observer_match WHERE signup_id = ? AND match_status = 'PROPOSED')
                    OR EXISTS(SELECT 1 FROM observer_request WHERE signup_id = ?)
                    OR EXISTS(SELECT 1 FROM observation WHERE signup_id = ?)
                """, Boolean.class, signupId, signupId, signupId));
    }
}
