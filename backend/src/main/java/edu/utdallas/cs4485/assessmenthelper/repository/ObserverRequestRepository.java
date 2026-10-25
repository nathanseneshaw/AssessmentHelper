package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.ObserverRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface ObserverRequestRepository extends ListCrudRepository<ObserverRequest, Integer> {

    List<ObserverRequest> findBySignupId(Integer signupId);

    List<ObserverRequest> findByObserverId(Integer observerId);

    List<ObserverRequest> findByStatusAndExpirationDateBefore(String status, LocalDateTime cutoff);
}
