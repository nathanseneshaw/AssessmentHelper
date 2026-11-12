package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.ObserverRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.repository.ListCrudRepository;

public interface ObserverRequestRepository extends ListCrudRepository<ObserverRequest, Integer> {
    List<ObserverRequest> findBySignupId(Integer signupId);

    List<ObserverRequest> findBySignupIdIn(Collection<Integer> signupIds);

    List<ObserverRequest> findByObserverId(Integer observerId);

    List<ObserverRequest> findByStatusAndExpirationDateBefore(String status, LocalDateTime cutoff);

    record EligibilityInvitationRow(
            Integer requestId,
            Integer signupId,
            Integer observerId,
            LocalDateTime requestDate,
            LocalDateTime expirationDate,
            LocalDateTime responseDate,
            String status,
            Boolean assignedByAc,
            String observerName) {}

    record EligibilityRequestRow(
            Integer requestId,
            Integer signupId,
            Integer professorId,
            String observeeName,
            String observerName,
            String status,
            @Embedded(onEmpty = Embedded.OnEmpty.USE_EMPTY) CourseSectionRepository.EligibilitySectionRow section,
            LocalDate windowStart,
            LocalDate windowEnd,
            Boolean assignedByAc,
            LocalDate observationDate) {}

    @Query("""
            SELECT ob.professor_id FROM observer_request r JOIN observer ob ON ob.observer_id=r.observer_id
            WHERE r.request_id=:requestId
            """)
    Optional<Integer> findObserverProfessorId(Integer requestId);

    @Query("""
            SELECT r.*, p.first_name || ' ' || p.last_name AS observer_name
            FROM observer_request r JOIN observer ob ON ob.observer_id=r.observer_id
            JOIN professor p ON p.professor_id=ob.professor_id
            JOIN evaluation_signup es ON es.signup_id=r.signup_id
            JOIN observee oe ON oe.observee_id=es.observee_id
            WHERE oe.cycle_id=:cycleId ORDER BY r.request_date DESC, r.request_id DESC
            """)
    List<EligibilityInvitationRow> findEligibilityInvitations(Integer cycleId);

    @Modifying
    @Query("""
            UPDATE observer_request SET status='EXPIRED' WHERE status='PENDING' AND expiration_date<=:now
            AND signup_id IN (SELECT es.signup_id FROM evaluation_signup es JOIN observee oe ON oe.observee_id=es.observee_id WHERE oe.cycle_id=:cycleId)
            """)
    void expirePendingInCycle(LocalDateTime now, Integer cycleId);

    @Query("""
            SELECT r.request_id, r.signup_id, ob.professor_id, p.first_name || ' ' || p.last_name AS observee_name,
            op.first_name || ' ' || op.last_name AS observer_name, r.status, s.*, co.course_code, co.course_number,
            co.course_name, ls.window_start, ls.window_end, r.assigned_by_ac, o.observation_date FROM observer_request r
            JOIN observer ob ON ob.observer_id=r.observer_id JOIN professor op ON op.professor_id=ob.professor_id
            JOIN evaluation_signup es ON es.signup_id=r.signup_id JOIN observee oe ON oe.observee_id=es.observee_id
            JOIN professor p ON p.professor_id=oe.professor_id JOIN course_section s ON s.section_id=es.section_id
            JOIN course co ON co.course_id=s.course_id JOIN observer_list_state ls ON ls.signup_id=es.signup_id
            LEFT JOIN observation o ON o.signup_id=r.signup_id AND o.observer_id=r.observer_id
            WHERE oe.cycle_id=:cycleId ORDER BY r.request_id DESC
            """)
    List<EligibilityRequestRow> findEligibilityRequests(Integer cycleId);

    @Modifying
    @Query("UPDATE observer_request SET status=:status,response_date=:responseDate WHERE request_id=:requestId")
    void updateStatusAndResponseDate(Integer requestId, String status, LocalDateTime responseDate);
}
