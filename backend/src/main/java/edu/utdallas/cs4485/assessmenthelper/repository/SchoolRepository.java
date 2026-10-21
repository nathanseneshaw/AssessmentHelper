package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.School;
import org.springframework.data.repository.ListCrudRepository;

public interface SchoolRepository extends ListCrudRepository<School, Integer> {}
