package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationSignup;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;
import org.springframework.data.repository.ListCrudRepository;

public interface EvaluationSignupRepository extends ListCrudRepository<EvaluationSignup, Integer> {
    List<EvaluationSignup> findByObserveeIdIn(Collection<Integer> observeeIds);

    @Lock(LockMode.PESSIMISTIC_WRITE)
    Optional<EvaluationSignup> findForUpdateBySignupId(Integer signupId);

    @Query("""
            SELECT oe.professor_id FROM evaluation_signup es JOIN observee oe ON oe.observee_id=es.observee_id
            WHERE oe.cycle_id=:cycleId AND es.status<>'CANCELLED'
            """)
    List<Integer> findSignedUpProfessorIds(Integer cycleId);

    public record TrackedObservationRow(
            Integer signupId,
            Integer observeeId,
            String observee,
            Integer observerId,
            String observer,
            String courseCode,
            String courseNumber,
            String sectionNumber,
            LocalDateTime releasedAt,
            Integer observationId,
            LocalDate observationDate,
            LocalTime startTime,
            String status,
            Boolean observeeSigned,
            Boolean observerSigned,
            LocalDateTime completedAt,
            String signupStatus,
            LocalDate observationDeadline,
            String reason,
            LocalDateTime deferredAt,
            String deferredBy,
            String targetSemester) {}

    @Query("""
            SELECT es.signup_id, oe.professor_id AS observee_id, p.first_name || ' ' || p.last_name AS observee,
            ob.professor_id AS observer_id, op.first_name || ' ' || op.last_name AS observer,
            c.course_code, c.course_number, s.section_number, ls.released_at, o.observation_id,
            o.observation_date, o.start_time, o.status, o.observee_signed, o.observer_signed, o.completed_at,
            es.status AS signup_status, ec.observation_deadline, d.reason, d.deferred_at,
            dp.first_name || ' ' || dp.last_name AS deferred_by,
            ds.term || ' ' || ds.academic_year AS target_semester
            FROM evaluation_signup es JOIN observee oe ON oe.observee_id=es.observee_id
            JOIN evaluation_cycle ec ON ec.cycle_id=oe.cycle_id
            JOIN professor p ON p.professor_id=oe.professor_id
            JOIN course_section s ON s.section_id=es.section_id JOIN course c ON c.course_id=s.course_id
            LEFT JOIN observer_list_state ls ON ls.signup_id=es.signup_id
            LEFT JOIN observation o ON o.signup_id=es.signup_id
            LEFT JOIN observer ob ON ob.observer_id=o.observer_id
            LEFT JOIN professor op ON op.professor_id=ob.professor_id
            LEFT JOIN evaluation_deferral d ON d.signup_id=es.signup_id
            LEFT JOIN professor dp ON dp.professor_id=d.deferred_by
            LEFT JOIN semester ds ON ds.semester_id=d.target_semester_id
            WHERE oe.cycle_id=:cycleId AND es.status<>'CANCELLED' ORDER BY es.signup_id
            """)
    List<TrackedObservationRow> findTrackedObservations(Integer cycleId);

    @Query("""
            SELECT c.course_code || ' ' || c.course_number || '.' || s.section_number
            FROM evaluation_signup es JOIN observee oe ON oe.observee_id=es.observee_id
            JOIN course_section s ON s.section_id=es.section_id JOIN course c ON c.course_id=s.course_id
            WHERE oe.professor_id=:professorId AND oe.cycle_id=:cycleId AND es.status<>'CANCELLED'
            """)
    List<String> findSignupCourseLabels(Integer professorId, Integer cycleId);

    public record MissingSignupRow(
            Integer signupId,
            Integer observeeId,
            String observee,
            String courseCode,
            String courseNumber,
            String sectionNumber,
            String observer,
            String status) {}

    @Query("""
            SELECT es.signup_id, oe.professor_id AS observee_id, p.first_name || ' ' || p.last_name AS observee,
            c.course_code, c.course_number, s.section_number,
            op.first_name || ' ' || op.last_name AS observer, o.status
            FROM evaluation_signup es JOIN observee oe ON oe.observee_id=es.observee_id
            JOIN professor p ON p.professor_id=oe.professor_id
            JOIN course_section s ON s.section_id=es.section_id JOIN course c ON c.course_id=s.course_id
            LEFT JOIN observation o ON o.signup_id=es.signup_id
            LEFT JOIN observer ob ON ob.observer_id=o.observer_id
            LEFT JOIN professor op ON op.professor_id=ob.professor_id
            WHERE oe.cycle_id=:cycleId AND es.status NOT IN ('CANCELLED', 'DEFERRED')
            AND NOT EXISTS (SELECT 1 FROM observation d WHERE d.signup_id=es.signup_id
            AND UPPER(d.status)='COMPLETED')
            ORDER BY p.last_name, p.first_name, es.signup_id,
            CASE WHEN UPPER(o.status)='SUBMITTED' THEN 1 ELSE 0 END, o.observation_id
            """)
    List<MissingSignupRow> findMissingSignups(Integer cycleId);

    @Query("""
            SELECT COUNT(*) FROM evaluation_signup es JOIN observee oe ON oe.observee_id=es.observee_id
            WHERE oe.professor_id=:professorId AND oe.cycle_id=:cycleId AND es.status NOT IN ('CANCELLED', 'DEFERRED')
            """)
    int countActiveSignupsInCycle(Integer professorId, Integer cycleId);

    record EligibilitySignupRow(
            Integer signupId,
            Integer professorId,
            String name,
            String departmentName,
            @Embedded(onEmpty = Embedded.OnEmpty.USE_EMPTY) CourseSectionRepository.EligibilitySectionRow section,
            LocalDate windowStart,
            LocalDate windowEnd,
            LocalDateTime releasedAt,
            String observerName) {}

    record SignupOwnerRow(Integer professorId, Integer sectionId, Integer cycleId) {}

    record EligibilityQualifyingCourseRow(
            String courseCode,
            String courseNumber,
            String sectionNumber,
            String term,
            Integer academicYear,
            LocalDate startDate,
            String targetNumber) {}

    @Query("""
            SELECT oe.professor_id, es.section_id, oe.cycle_id FROM evaluation_signup es
            JOIN observee oe ON oe.observee_id=es.observee_id WHERE es.signup_id=:signupId
            """)
    Optional<SignupOwnerRow> findOwner(Integer signupId);

    @Query("""
            SELECT es.signup_id, p.professor_id, p.first_name || ' ' || p.last_name AS name,
            d.department_name, s.*, c.course_code, c.course_number, c.course_name,
            ls.window_start, ls.window_end, ls.released_at, op.first_name || ' ' || op.last_name AS observer_name
            FROM evaluation_signup es JOIN observee oe ON oe.observee_id=es.observee_id
            JOIN professor p ON p.professor_id=oe.professor_id
            JOIN department d ON d.department_id=p.department_id
            JOIN course_section s ON s.section_id=es.section_id JOIN course c ON c.course_id=s.course_id
            JOIN observer_list_state ls ON ls.signup_id=es.signup_id
            LEFT JOIN observation o ON o.signup_id=es.signup_id LEFT JOIN observer ob ON ob.observer_id=o.observer_id
            LEFT JOIN professor op ON op.professor_id=ob.professor_id
            WHERE oe.cycle_id=:cycleId AND es.status<>'CANCELLED' ORDER BY es.signup_id
            """)
    List<EligibilitySignupRow> findEligibilitySignups(Integer cycleId);

    @Query("""
            SELECT DISTINCT c.course_code, c.course_number, s.section_number,
                taught_sem.term, taught_sem.academic_year, taught_sem.start_date,
                target_course.course_number AS target_number
            FROM evaluation_signup es
            JOIN observee oe ON oe.observee_id=es.observee_id
            JOIN professor target_prof ON target_prof.professor_id=oe.professor_id
            JOIN course_section target_section ON target_section.section_id=es.section_id
            JOIN course target_course ON target_course.course_id=target_section.course_id
            JOIN teaching_history t ON t.professor_id=:professorId
            JOIN course_section s ON s.section_id=t.section_id
            JOIN semester taught_sem ON taught_sem.semester_id=s.semester_id
            JOIN semester target_sem ON target_sem.semester_id=target_section.semester_id
            JOIN course c ON c.course_id=s.course_id AND c.department_id=target_prof.department_id
            WHERE es.signup_id=:signupId AND taught_sem.start_date<=target_sem.start_date
                AND taught_sem.end_date>=DATEADD('YEAR', -2, target_sem.start_date)
            ORDER BY c.course_code, c.course_number, s.section_number, taught_sem.start_date
            """)
    List<EligibilityQualifyingCourseRow> findQualifyingCourses(Integer professorId, Integer signupId);

    @Modifying
    @Query("UPDATE evaluation_signup SET status='DEFERRED' WHERE signup_id=:signupId")
    void markDeferred(Integer signupId);
}
