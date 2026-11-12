package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.TeachingHistoryRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.TeachingHistoryRepository.TeachingRow;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeachingHistoryService {

    private final TeachingHistoryRepository teachingHistoryRepository;
    private final ProfessorRepository professorRepository;

    public TeachingHistoryService(
            TeachingHistoryRepository teachingHistoryRepository, ProfessorRepository professorRepository) {
        this.teachingHistoryRepository = teachingHistoryRepository;
        this.professorRepository = professorRepository;
    }

    /**
     * Lists every section the professor has taught, newest semester first. Sections in the same semester are ordered
     * by course number, then section number. Semesters without a start date come last.
     *
     * @throws NotFoundException if no professor has this id. A professor who has never taught gets an empty list
     *     instead.
     */
    @Transactional(readOnly = true)
    public List<TeachingHistoryEntry> findByProfessor(Integer professorId) {
        List<TeachingRow> rows = teachingHistoryRepository.findTeachingRows(professorId);
        if (rows.isEmpty() && !professorRepository.existsById(professorId)) {
            throw new NotFoundException("No professor with id " + professorId);
        }
        return rows.stream().map(TeachingHistoryService::toEntry).toList();
    }

    private static TeachingHistoryEntry toEntry(TeachingRow row) {
        int level = ObserverEligibilityService.level(row.courseNumber());
        return new TeachingHistoryEntry(
                row.sectionId(),
                row.courseId(),
                row.courseCode(),
                row.courseNumber(),
                row.courseName(),
                level < 0 ? null : level,
                row.sectionNumber(),
                row.term(),
                row.academicYear(),
                row.startDate(),
                row.endDate());
    }
}
