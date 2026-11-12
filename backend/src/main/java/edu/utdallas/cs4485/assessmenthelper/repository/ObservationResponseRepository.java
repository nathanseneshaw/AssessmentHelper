package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.ObservationResponse;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface ObservationResponseRepository extends ListCrudRepository<ObservationResponse, Integer> {
    List<ObservationResponse> findByObservationId(Integer observationId);

    record ObservationAnswerRow(int criterionId, Integer rating, String comments) {}

    @Query("""
            SELECT r.criterion_id, r.rating, r.comments FROM observation_response r
            JOIN observation_criterion c ON c.criterion_id=r.criterion_id
            WHERE r.observation_id=:observationId ORDER BY c.display_order, c.criterion_id, r.response_id
            """)
    List<ObservationAnswerRow> findAnswersByObservationId(int observationId);

    @Modifying
    @Query("DELETE FROM observation_response WHERE observation_id=:observationId")
    void deleteAnswersByObservationId(int observationId);
}
