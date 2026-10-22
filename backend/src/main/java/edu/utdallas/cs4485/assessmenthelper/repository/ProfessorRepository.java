package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface ProfessorRepository extends ListCrudRepository<Professor, Integer> {
    // Serialize signup changes for a professor, including creation of their observee row.
    @Query("SELECT * FROM professor WHERE professor_id = :professorId FOR UPDATE")
    Optional<Professor> findForSignupUpdate(Integer professorId);
}
