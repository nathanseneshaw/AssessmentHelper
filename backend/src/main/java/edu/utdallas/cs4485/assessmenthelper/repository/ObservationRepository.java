package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.Observation;
import org.springframework.data.repository.ListCrudRepository;

public interface ObservationRepository extends ListCrudRepository<Observation, Integer> {
}
