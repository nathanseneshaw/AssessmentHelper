package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.ObservationAuditLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

public interface ObservationAuditLogRepository extends Repository<ObservationAuditLog, Integer> {

    List<ObservationAuditLog> findByObservationIdOrderByLogIdAsc(Integer observationId);

    @Modifying
    @Query(
            "INSERT INTO observation_audit_log(observation_id,event_type,actor_professor_id,recipient_professor_id,occurred_at) VALUES (:observationId,:eventType,:actorProfessorId,:recipientProfessorId,:occurredAt)")
    void append(
            int observationId,
            String eventType,
            Integer actorProfessorId,
            Integer recipientProfessorId,
            LocalDateTime occurredAt);
}
