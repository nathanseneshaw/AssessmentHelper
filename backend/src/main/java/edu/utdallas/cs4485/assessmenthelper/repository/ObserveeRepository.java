package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.Observee;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface ObserveeRepository extends ListCrudRepository<Observee, Integer> {

    List<Observee> findByProfessorId(Integer professorId);
}
