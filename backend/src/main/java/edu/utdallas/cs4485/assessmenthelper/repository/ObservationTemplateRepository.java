package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.ObservationTemplate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface ObservationTemplateRepository extends ListCrudRepository<ObservationTemplate, Integer> {
    List<ObservationTemplate> findByActiveStatusTrue();

    Optional<ObservationTemplate> findFirstByActiveStatusTrueOrderByVersionDescTemplateIdDesc();

    @Query("SELECT template_id FROM observation_template ORDER BY version DESC,template_id DESC")
    List<Integer> findTemplateIdsNewestFirst();

    @Modifying
    @Query("UPDATE observation_template SET active_status=FALSE WHERE active_status=TRUE")
    void deactivateActiveTemplates();

    @Modifying
    @Query("UPDATE observation_template SET active_status=TRUE WHERE template_id=:templateId")
    void activateTemplate(int templateId);

    @Query(
            "SELECT template_id FROM observation_template WHERE template_id=(SELECT MIN(template_id) FROM observation_template) FOR UPDATE")
    Optional<Integer> lockFirstTemplateId();

    @Query("SELECT COALESCE(MAX(version),0)+1 FROM observation_template")
    int nextTemplateVersion();
}
