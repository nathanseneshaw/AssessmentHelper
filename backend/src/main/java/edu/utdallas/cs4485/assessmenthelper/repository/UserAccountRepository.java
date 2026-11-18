package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.UserAccount;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface UserAccountRepository extends ListCrudRepository<UserAccount, Integer> {

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    Optional<UserAccount> findByProfessorId(Integer professorId);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsername(String username);

    record DemoAccountRow(
            Integer userId, Integer professorId, String name, String email, String departmentName, Boolean committee) {}

    @Query("""
            SELECT u.user_id, p.professor_id, p.first_name || ' ' || p.last_name AS name, u.email, d.department_name,
                EXISTS (SELECT 1 FROM committee_member c WHERE c.professor_id=p.professor_id AND c.active_status=TRUE
                    AND c.start_date<=:today AND (c.end_date IS NULL OR c.end_date>=:today)) AS committee
            FROM user_account u JOIN professor p ON p.professor_id=u.professor_id
            JOIN department d ON d.department_id=p.department_id
            WHERE u.is_active=TRUE AND p.active_status=TRUE
            ORDER BY p.last_name, p.first_name, u.user_id
            """)
    List<DemoAccountRow> findDemoAccounts(LocalDate today);
}
