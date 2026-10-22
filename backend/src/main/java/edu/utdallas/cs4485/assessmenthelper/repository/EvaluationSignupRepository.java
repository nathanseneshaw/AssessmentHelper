package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationSignup;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface EvaluationSignupRepository extends ListCrudRepository<EvaluationSignup, Integer> {

    List<EvaluationSignup> findByObserveeIdIn(Collection<Integer> observeeIds);

    @Query("SELECT * FROM evaluation_signup WHERE signup_id = :signupId FOR UPDATE")
    Optional<EvaluationSignup> findForRequestUpdate(Integer signupId);
}
