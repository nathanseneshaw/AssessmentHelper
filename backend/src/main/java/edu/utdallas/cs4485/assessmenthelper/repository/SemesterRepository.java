package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.Semester;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface SemesterRepository extends ListCrudRepository<Semester, Integer> {
    @Query("""
            SELECT s.semester_id FROM semester s WHERE s.start_date >
            (SELECT current_sem.end_date FROM semester current_sem WHERE current_sem.semester_id=:semesterId)
            ORDER BY s.start_date,s.semester_id FETCH FIRST 1 ROW ONLY
            """)
    Optional<Integer> findNextSemesterId(Integer semesterId);
}
