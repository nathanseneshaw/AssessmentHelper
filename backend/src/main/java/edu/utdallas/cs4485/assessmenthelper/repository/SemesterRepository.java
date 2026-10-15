package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.Semester;
import org.springframework.data.repository.ListCrudRepository;

public interface SemesterRepository extends ListCrudRepository<Semester, Integer> {}
