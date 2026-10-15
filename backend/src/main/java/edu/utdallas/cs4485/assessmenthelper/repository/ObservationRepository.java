package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.Observation;
import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface ObservationRepository extends ListCrudRepository<Observation, Integer> {

    List<Observation> findBySignupIdIn(Collection<Integer> signupIds);
}
