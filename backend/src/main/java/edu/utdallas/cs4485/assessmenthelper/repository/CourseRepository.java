package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.Course;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface CourseRepository extends ListCrudRepository<Course, Integer> {

    List<Course> findByCourseNumber(String courseNumber);
}
