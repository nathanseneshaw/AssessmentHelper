package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationDeferral;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface EvaluationDeferralRepository extends ListCrudRepository<EvaluationDeferral, Integer> {
    List<EvaluationDeferral> findByProfessorId(Integer professorId);
}
