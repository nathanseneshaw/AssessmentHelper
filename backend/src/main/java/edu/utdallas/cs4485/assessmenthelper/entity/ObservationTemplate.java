package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDate;

/** Foreign keys are plain ids: createdBy -> user_account. */
@Table("observation_template")
public record ObservationTemplate(
        @Id Integer templateId,
        Integer createdBy,
        String templateName,
        Integer version,
        LocalDate createdDate,
        Boolean activeStatus) {
}
