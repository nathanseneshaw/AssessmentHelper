package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.CourseSection;
import org.springframework.data.repository.ListCrudRepository;

public interface CourseSectionRepository extends ListCrudRepository<CourseSection, Integer> {
}
