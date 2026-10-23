package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.TeachingHistory;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface TeachingHistoryRepository extends ListCrudRepository<TeachingHistory, Integer> {

    List<TeachingHistory> findByProfessorId(Integer professorId);
}
