package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.Course;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

public interface CourseRepository extends ListCrudRepository<Course, Integer> {
    Optional<Course> findFirstByCourseCodeIgnoreCaseAndCourseNumberOrderByCourseId(
            String courseCode, String courseNumber);

    Optional<Course> findFirstByDepartmentIdAndCourseNumber(Integer departmentId, String courseNumber);

    Optional<Course> findFirstByDepartmentIdOrderByCourseId(Integer departmentId);
}
