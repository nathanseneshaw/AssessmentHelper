package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationSignup;
import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface EvaluationSignupRepository extends ListCrudRepository<EvaluationSignup, Integer> {

    List<EvaluationSignup> findByObserveeIdIn(Collection<Integer> observeeIds);
}
