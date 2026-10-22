package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.CourseSection;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface CourseSectionRepository extends ListCrudRepository<CourseSection, Integer> {
    @Query("""
            SELECT s.* FROM course_section s
            WHERE (:semesterId IS NULL OR s.semester_id = :semesterId)
              AND (:courseId IS NULL OR s.course_id = :courseId)
              AND (:professorId IS NULL OR EXISTS (
                  SELECT 1 FROM teaching_history t
                  WHERE t.section_id = s.section_id AND t.professor_id = :professorId))
            ORDER BY s.course_id, s.section_number, s.section_id
            """)
    List<CourseSection> findFiltered(Integer semesterId, Integer courseId, Integer professorId);
}
