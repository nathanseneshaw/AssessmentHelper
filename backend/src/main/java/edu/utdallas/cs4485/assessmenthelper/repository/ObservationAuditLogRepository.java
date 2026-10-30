package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.ObservationAuditLog;
import java.util.List;
import org.springframework.data.repository.Repository;

public interface ObservationAuditLogRepository extends Repository<ObservationAuditLog, Integer> {

    List<ObservationAuditLog> findByObservationIdOrderByLogIdAsc(Integer observationId);
}
