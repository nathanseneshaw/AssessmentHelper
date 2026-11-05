package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.MessageTemplate;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

public interface MessageTemplateRepository extends ListCrudRepository<MessageTemplate, Integer> {
    Optional<MessageTemplate> findByTemplateType(String templateType);
}
