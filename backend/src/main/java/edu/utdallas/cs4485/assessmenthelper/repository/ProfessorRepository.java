package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;
import org.springframework.data.repository.ListCrudRepository;

public interface ProfessorRepository extends ListCrudRepository<Professor, Integer> {
    List<Professor> findByDepartmentIdOrderByLastNameAscFirstNameAsc(Integer departmentId);

    // Serialize signup changes for a professor, including creation of their observee row.
    @Lock(LockMode.PESSIMISTIC_WRITE)
    Optional<Professor> findForUpdateByProfessorId(Integer professorId);

    public record DashboardProfileRow(String name, String professorRank, String departmentName, String schoolName) {}

    @Query("""
            SELECT p.first_name || ' ' || p.last_name AS name, p.professor_rank, d.department_name, s.school_name
            FROM professor p JOIN department d ON d.department_id=p.department_id
            JOIN school s ON s.school_id=d.school_id WHERE p.professor_id=:professorId
            """)
    Optional<DashboardProfileRow> findDashboardProfile(Integer professorId);

    @Query("""
            SELECT COUNT(*) FROM professor p JOIN evaluation_policy ep ON ep.professor_rank=p.professor_rank
            WHERE p.professor_id=:professorId AND p.active_status=TRUE AND ep.active_status=TRUE
            """)
    int countApplicableEvaluationPolicies(Integer professorId);

    record EligibilityPersonRow(Integer professorId, String name, String departmentName) {}

    record EligibilityTargetRow(Integer departmentId, String courseNumber) {}

    @Query("""
            SELECT p.professor_id, p.first_name || ' ' || p.last_name AS name, d.department_name
            FROM professor p JOIN department d ON d.department_id=p.department_id
            WHERE p.active_status=TRUE AND p.professor_id=:professorId
            """)
    Optional<EligibilityPersonRow> findActivePerson(Integer professorId);

    @Query("""
            SELECT p.department_id, co.course_number FROM professor p, course_section s
            JOIN course co ON co.course_id=s.course_id WHERE p.professor_id=:professorId AND s.section_id=:sectionId
            """)
    Optional<EligibilityTargetRow> findEligibilityTarget(Integer professorId, Integer sectionId);
}
