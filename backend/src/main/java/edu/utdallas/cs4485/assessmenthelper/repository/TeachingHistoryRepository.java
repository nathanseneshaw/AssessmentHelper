package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.TeachingHistory;
import org.springframework.data.repository.ListCrudRepository;

public interface TeachingHistoryRepository extends ListCrudRepository<TeachingHistory, Integer> {
    boolean existsByProfessorIdAndSectionId(Integer professorId, Integer sectionId);
}
