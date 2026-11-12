package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.Observer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface ObserverRepository extends ListCrudRepository<Observer, Integer> {
    List<Observer> findByCycleId(Integer cycleId);

    List<Observer> findByProfessorId(Integer professorId);

    List<Observer> findByProfessorIdAndCycleId(Integer professorId, Integer cycleId);

    record EligibilityCandidateRow(
            Integer observerId, Integer professorId, String name, String departmentName, String courseNumber) {}

    record EligibilityPoolRow(Integer observerId, Integer professorId) {}

    @Query("""
            SELECT ob.observer_id, p.professor_id, p.first_name || ' ' || p.last_name AS name,
            d.department_name, c.course_number FROM observer ob
            JOIN professor p ON p.professor_id=ob.professor_id
            JOIN department d ON d.department_id=p.department_id
            JOIN evaluation_signup es ON es.signup_id=:signupId
            JOIN course_section s ON s.section_id=es.section_id JOIN course c ON c.course_id=s.course_id
            WHERE ob.observer_id=:observerId
            """)
    Optional<EligibilityCandidateRow> findEligibilityCandidate(Integer signupId, Integer observerId);

    @Query("""
            SELECT DISTINCT ob.observer_id, p.professor_id FROM observer ob
            JOIN professor p ON p.professor_id=ob.professor_id
            JOIN observee oe ON oe.professor_id=p.professor_id AND oe.cycle_id=ob.cycle_id
            JOIN evaluation_signup es ON es.observee_id=oe.observee_id
            WHERE ob.cycle_id=:cycleId AND ob.active_status=TRUE AND p.active_status=TRUE
            AND p.department_id=:departmentId AND p.professor_id<>:professorId AND es.status<>'CANCELLED'
            """)
    List<EligibilityPoolRow> findEligibilityPool(Integer cycleId, Integer departmentId, Integer professorId);

    @Modifying
    @Query("UPDATE observer SET active_status=TRUE WHERE professor_id=:professorId AND cycle_id=:cycleId")
    void activate(Integer professorId, Integer cycleId);
}
