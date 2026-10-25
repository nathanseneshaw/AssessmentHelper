package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface ProfessorRepository extends ListCrudRepository<Professor, Integer> {

    List<Professor> findByDepartmentIdOrderByLastNameAscFirstNameAsc(Integer departmentId);
}
