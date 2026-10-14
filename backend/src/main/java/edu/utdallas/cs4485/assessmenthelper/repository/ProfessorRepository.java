package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import org.springframework.data.repository.ListCrudRepository;

public interface ProfessorRepository extends ListCrudRepository<Professor, Integer> {
}
