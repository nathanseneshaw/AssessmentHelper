package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.CourseSection;
import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface CourseSectionRepository extends ListCrudRepository<CourseSection, Integer> {

    List<CourseSection> findByCourseIdInAndSectionNumber(Collection<Integer> courseIds, String sectionNumber);

    List<CourseSection> findBySemesterId(Integer semesterId);
}
