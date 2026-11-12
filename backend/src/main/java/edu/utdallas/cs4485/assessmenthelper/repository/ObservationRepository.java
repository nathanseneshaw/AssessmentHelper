package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.Observation;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;
import org.springframework.data.repository.ListCrudRepository;

public interface ObservationRepository extends ListCrudRepository<Observation, Integer> {
    List<Observation> findBySignupIdIn(Collection<Integer> signupIds);

    @Lock(LockMode.PESSIMISTIC_WRITE)
    Optional<Observation> findForUpdateByObservationId(Integer observationId);

    Optional<Observation> findBySignupIdAndObserverId(Integer signupId, Integer observerId);

    // observer_signed_at is when the observer submitted the form; observee_signed_at is when the observee signed off.
    String OBSERVATION_DETAIL_SELECT = """
            SELECT o.observation_id, o.signup_id, o.status, o.template_id, o.observation_date,
            o.start_time AS observation_start, o.observer_signed_at, o.observee_signed_at, o.observee_comments,
            de.school_id AS observee_school_id, oe.cycle_id, sm.term, sm.academic_year,
            pe.professor_id AS observee_professor_id, pe.first_name || ' ' || pe.last_name AS observee_name,
            de.department_name AS observee_department,
            po.professor_id AS observer_professor_id, po.first_name || ' ' || po.last_name AS observer_name,
            dp.department_name AS observer_department, s.section_id, s.section_number, s.meeting_days,
            s.start_time AS section_start, s.end_time AS section_end, s.location,
            c.course_code, c.course_number, c.course_name
            FROM observation o
            JOIN observer ob ON ob.observer_id=o.observer_id JOIN professor po ON po.professor_id=ob.professor_id
            JOIN department dp ON dp.department_id=po.department_id
            JOIN evaluation_signup es ON es.signup_id=o.signup_id JOIN observee oe ON oe.observee_id=es.observee_id
            JOIN professor pe ON pe.professor_id=oe.professor_id JOIN department de ON de.department_id=pe.department_id
            JOIN course_section s ON s.section_id=es.section_id JOIN course c ON c.course_id=s.course_id
            JOIN semester sm ON sm.semester_id=s.semester_id
            """;

    // Observations not yet dated (just accepted) come first, then the most recent.
    String OBSERVATION_DETAIL_NEWEST_FIRST = " ORDER BY o.observation_date DESC NULLS FIRST, o.observation_id DESC";

    record ObservationDetailRow(
            int observationId,
            int signupId,
            String status,
            int templateId,
            LocalDate observationDate,
            LocalTime observationStart,
            LocalDateTime observerSignedAt,
            LocalDateTime observeeSignedAt,
            String observeeComments,
            int observeeSchoolId,
            int cycleId,
            String term,
            int academicYear,
            int observeeProfessorId,
            String observeeName,
            String observeeDepartment,
            int observerProfessorId,
            String observerName,
            String observerDepartment,
            int sectionId,
            String sectionNumber,
            String meetingDays,
            LocalTime sectionStart,
            LocalTime sectionEnd,
            String location,
            String courseCode,
            String courseNumber,
            String courseName) {}

    @Query(OBSERVATION_DETAIL_SELECT + "WHERE o.observation_id=:observationId" + OBSERVATION_DETAIL_NEWEST_FIRST)
    Optional<ObservationDetailRow> findDetailByObservationId(int observationId);

    @Query(OBSERVATION_DETAIL_SELECT
            + "WHERE po.professor_id=:professorId OR pe.professor_id=:professorId"
            + OBSERVATION_DETAIL_NEWEST_FIRST)
    List<ObservationDetailRow> findDetailsByProfessorId(int professorId);

    @Modifying
    @Query(
            "UPDATE observation SET status=:status, observation_date=:observationDate, start_time=:startTime WHERE observation_id=:observationId")
    void updateStarted(String status, LocalDate observationDate, LocalTime startTime, int observationId);

    @Modifying
    @Query(
            "UPDATE observation SET status=:status, observer_signed=TRUE, observer_signed_at=:observerSignedAt WHERE observation_id=:observationId")
    void updateObserverSubmitted(String status, LocalDateTime observerSignedAt, int observationId);

    @Modifying
    @Query(
            "UPDATE observation SET status=:status, observee_signed=TRUE, observee_signed_at=:observeeSignedAt, completed_at=:completedAt, observee_comments=:observeeComments WHERE observation_id=:observationId")
    void updateObserveeSignedOff(
            String status,
            LocalDateTime observeeSignedAt,
            LocalDateTime completedAt,
            String observeeComments,
            int observationId);

    public record DashboardObservationRow(
            Integer observationId,
            LocalDate observationDate,
            LocalTime startTime,
            String status,
            LocalDateTime completedAt,
            Integer cycleId,
            String term,
            Integer academicYear,
            LocalDate observationDeadline,
            Integer observeeId,
            String observee,
            String observer,
            String courseCode,
            String courseNumber,
            String sectionNumber,
            Boolean feedbackGiven) {}

    @Query("""
            SELECT o.observation_id, o.observation_date, o.start_time, o.status, o.completed_at, oe.cycle_id,
            sm.term, sm.academic_year, ec.observation_deadline, pe.professor_id AS observee_id,
            pe.first_name || ' ' || pe.last_name AS observee, po.first_name || ' ' || po.last_name AS observer,
            c.course_code, c.course_number, s.section_number,
            EXISTS (SELECT 1 FROM process_feedback f WHERE f.signup_id=o.signup_id AND f.professor_id=:professorId)
                AS feedback_given
            FROM observation o JOIN evaluation_signup es ON es.signup_id=o.signup_id
            JOIN observee oe ON oe.observee_id=es.observee_id JOIN professor pe ON pe.professor_id=oe.professor_id
            JOIN observer ob ON ob.observer_id=o.observer_id JOIN professor po ON po.professor_id=ob.professor_id
            JOIN course_section s ON s.section_id=es.section_id JOIN course c ON c.course_id=s.course_id
            JOIN evaluation_cycle ec ON ec.cycle_id=oe.cycle_id JOIN semester sm ON sm.semester_id=ec.semester_id
            WHERE (pe.professor_id=:professorId OR po.professor_id=:professorId) AND es.status<>'CANCELLED'
            ORDER BY o.observation_date DESC NULLS FIRST, o.observation_id DESC
            """)
    List<DashboardObservationRow> findDashboardObservations(Integer professorId);

    public record ObservationDeadlineRow(Integer observationId, LocalDate observationDeadline) {}

    @Query("""
            SELECT o.observation_id, ec.observation_deadline FROM observation o
            JOIN evaluation_signup es ON es.signup_id=o.signup_id
            JOIN observee oe ON oe.observee_id=es.observee_id
            JOIN observer ob ON ob.observer_id=o.observer_id
            JOIN evaluation_cycle ec ON ec.cycle_id=oe.cycle_id
            WHERE es.status NOT IN ('CANCELLED', 'DEFERRED')
            AND (oe.professor_id=:professorId OR ob.professor_id=:professorId)
            """)
    List<ObservationDeadlineRow> findObservationDeadlines(Integer professorId);

    @Query("""
            SELECT COUNT(*) FROM observation o JOIN evaluation_signup es ON es.signup_id=o.signup_id
            JOIN observee oe ON oe.observee_id=es.observee_id
            WHERE oe.professor_id=:professorId AND oe.cycle_id=:cycleId AND es.status NOT IN ('CANCELLED', 'DEFERRED')
            """)
    int countActiveObservationsInCycle(Integer professorId, Integer cycleId);

    @Query("""
            SELECT COUNT(*) FROM observation o
            JOIN evaluation_signup es ON es.signup_id=o.signup_id
            JOIN observee oe ON oe.observee_id=es.observee_id
            WHERE oe.cycle_id=:cycleId AND o.observation_date IS NOT NULL
                AND (o.observation_date<=:signupDeadline OR o.observation_date>:observationDeadline)
            """)
    Integer countScheduledOutsideDeadlines(Integer cycleId, LocalDate signupDeadline, LocalDate observationDeadline);

    // Spring Data binds a LocalTime as a TIMESTAMP on today's date, so the casts keep this a time-of-day comparison.
    @Query("""
            SELECT COUNT(*) FROM observation o JOIN observer ob ON ob.observer_id=o.observer_id
            JOIN evaluation_signup es ON es.signup_id=o.signup_id JOIN course_section cs ON cs.section_id=es.section_id
            WHERE ob.professor_id=:professorId AND o.signup_id<>:signupId AND o.observation_date=:observationDate
            AND o.start_time<CAST(:endTime AS TIME) AND cs.end_time>CAST(:startTime AS TIME)
            """)
    Integer countObserverTimeConflicts(
            Integer professorId, Integer signupId, LocalDate observationDate, LocalTime endTime, LocalTime startTime);

    @Modifying
    @Query("""
            UPDATE observation SET observation_date=:observationDate,start_time=:startTime,status='SCHEDULED'
            WHERE signup_id=:signupId AND observer_id=(SELECT observer_id FROM observer_request WHERE request_id=:requestId)
            AND observation_date IS NULL AND status='ACCEPTED'
            """)
    int scheduleAcceptedObservation(
            Integer signupId, Integer requestId, LocalDate observationDate, LocalTime startTime);
}
