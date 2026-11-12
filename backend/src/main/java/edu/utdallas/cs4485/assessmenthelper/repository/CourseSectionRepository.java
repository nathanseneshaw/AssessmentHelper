package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.CourseSection;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
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

    record EligibilitySectionRow(
            Integer sectionId,
            String courseCode,
            String courseNumber,
            String sectionNumber,
            String courseName,
            String meetingDays,
            LocalTime startTime,
            LocalTime endTime) {}

    @Query("""
            SELECT s.*, c.course_code, c.course_number, c.course_name
            FROM course_section s JOIN course c ON c.course_id=s.course_id WHERE s.section_id=:sectionId
            """)
    Optional<EligibilitySectionRow> findEligibilitySection(Integer sectionId);
}
