package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.ObserverNotification;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface ObserverNotificationRepository extends ListCrudRepository<ObserverNotification, Integer> {
    List<ObserverNotification> findByProfessorId(Integer professorId);

    List<ObserverNotification> findByProfessorIdOrderByNotificationIdDesc(Integer professorId);

    @Query("""
            SELECT n.* FROM observer_notification n JOIN observer_list_state ls ON ls.signup_id=n.signup_id
            JOIN evaluation_signup es ON es.signup_id=n.signup_id JOIN observee oe ON oe.observee_id=es.observee_id
            WHERE n.professor_id=:professorId AND oe.cycle_id=:cycleId ORDER BY n.notification_id DESC
            """)
    List<ObserverNotification> findListNotificationsInCycle(Integer professorId, Integer cycleId);
}
