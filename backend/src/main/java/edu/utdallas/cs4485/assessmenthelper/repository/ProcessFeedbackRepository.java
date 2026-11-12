package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.ProcessFeedback;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface ProcessFeedbackRepository extends ListCrudRepository<ProcessFeedback, Integer> {

    List<ProcessFeedback> findByProfessorId(Integer professorId);

    List<ProcessFeedback> findByCycleId(Integer cycleId);
}
