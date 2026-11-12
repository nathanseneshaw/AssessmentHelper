package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.ObservationCriterion;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface ObservationCriterionRepository extends ListCrudRepository<ObservationCriterion, Integer> {
    List<ObservationCriterion> findByTemplateIdOrderByDisplayOrderAscCriterionIdAsc(Integer templateId);

    List<ObservationCriterion> findByTemplateIdAndSelectedTrueOrderByDisplayOrderAscCriterionIdAsc(Integer templateId);
}
