package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.TeachingHistory;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface TeachingHistoryRepository extends ListCrudRepository<TeachingHistory, Integer> {
    List<TeachingHistory> findByProfessorId(Integer professorId);

    boolean existsByProfessorIdAndSectionId(Integer professorId, Integer sectionId);

    record TeachingRow(
            Integer sectionId,
            Integer courseId,
            String courseCode,
            String courseNumber,
            String courseName,
            String sectionNumber,
            String term,
            Integer academicYear,
            LocalDate startDate,
            LocalDate endDate) {}

    // NULLS LAST keeps semesters without a start date at the end on both H2 and PostgreSQL.
    @Query("""
            SELECT s.section_id, c.course_id, c.course_code, c.course_number, c.course_name, s.section_number,
                   sem.term, sem.academic_year, sem.start_date, sem.end_date
            FROM teaching_history t JOIN course_section s ON s.section_id=t.section_id
            JOIN course c ON c.course_id=s.course_id JOIN semester sem ON sem.semester_id=s.semester_id
            WHERE t.professor_id=:professorId
            ORDER BY sem.start_date DESC NULLS LAST, c.course_number, s.section_number, s.section_id
            """)
    List<TeachingRow> findTeachingRows(Integer professorId);

    @Query("""
            SELECT DISTINCT s.*, c.course_code, c.course_number, c.course_name
            FROM teaching_history t JOIN course_section s ON s.section_id=t.section_id
            JOIN course c ON c.course_id=s.course_id WHERE t.professor_id=:professorId AND s.semester_id=:semesterId
            ORDER BY s.section_id
            """)
    List<CourseSectionRepository.EligibilitySectionRow> findEligibilitySections(
            Integer professorId, Integer semesterId);

    @Query("""
            SELECT co.course_number FROM teaching_history t JOIN course_section s ON s.section_id=t.section_id
            JOIN course co ON co.course_id=s.course_id
            JOIN semester taught_sem ON taught_sem.semester_id=s.semester_id
            WHERE t.professor_id=:professorId AND taught_sem.start_date<=:latestStart AND taught_sem.end_date>=:earliestEnd
            AND co.department_id=:departmentId
            """)
    List<String> findCourseNumbersTaught(
            Integer professorId, LocalDate latestStart, LocalDate earliestEnd, Integer departmentId);
}
