package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.ObserverMatch;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface ObserverMatchRepository extends ListCrudRepository<ObserverMatch, Integer> {
    List<ObserverMatch> findBySignupId(Integer signupId);

    @Query("""
            SELECT ob.observer_id, p.professor_id, p.first_name || ' ' || p.last_name AS name,
            d.department_name, c.course_number FROM observer_match m
            JOIN observer ob ON ob.observer_id=m.observer_id JOIN professor p ON p.professor_id=ob.professor_id
            JOIN department d ON d.department_id=p.department_id
            JOIN evaluation_signup es ON es.signup_id=m.signup_id
            JOIN course_section s ON s.section_id=es.section_id JOIN course c ON c.course_id=s.course_id
            WHERE m.signup_id=:signupId AND m.match_status=:matchStatus ORDER BY m.match_id
            """)
    List<ObserverRepository.EligibilityCandidateRow> findEligibilityCandidates(Integer signupId, String matchStatus);

    @Query("""
            SELECT observer_id FROM observer_match WHERE signup_id=:signupId
            UNION SELECT observer_id FROM observer_request WHERE signup_id=:signupId
            """)
    List<Integer> findMatchedOrRequestedObserverIds(Integer signupId);

    @Modifying
    @Query("UPDATE observer_match SET match_status='SUPERSEDED' WHERE signup_id=:signupId AND match_status='PROPOSED'")
    void supersedeProposed(Integer signupId);
}
