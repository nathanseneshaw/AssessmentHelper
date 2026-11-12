package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.ObserverListState;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface ObserverListStateRepository extends ListCrudRepository<ObserverListState, Integer> {
    record EligibilityWindowRow(Integer signupId, LocalDate windowEnd) {}

    boolean existsBySignupIdAndReleasedAtIsNotNull(Integer signupId);

    @Query("""
            SELECT ls.signup_id, ls.window_end FROM observer_list_state ls
            JOIN evaluation_signup es ON es.signup_id=ls.signup_id
            JOIN observee oe ON oe.observee_id=es.observee_id WHERE oe.cycle_id=:cycleId
            """)
    List<EligibilityWindowRow> findWindowsInCycle(Integer cycleId);

    @Modifying
    @Query(
            "INSERT INTO observer_list_state(signup_id,window_start,window_end) VALUES (:signupId,:windowStart,:windowEnd)")
    void insertWindow(Integer signupId, LocalDate windowStart, LocalDate windowEnd);

    @Modifying
    @Query("UPDATE observer_list_state SET window_start=:windowStart, window_end=:windowEnd WHERE signup_id=:signupId")
    void updateWindow(Integer signupId, LocalDate windowStart, LocalDate windowEnd);

    @Modifying
    @Query("UPDATE observer_list_state SET released_at=:releasedAt WHERE signup_id=:signupId")
    void markReleased(Integer signupId, LocalDateTime releasedAt);

    @Modifying
    @Query("UPDATE observer_list_state SET released_at=NULL WHERE signup_id=:signupId")
    void clearReleasedAt(Integer signupId);
}
