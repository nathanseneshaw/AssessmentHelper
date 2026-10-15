package edu.utdallas.cs4485.assessmenthelper.entity;

import java.time.LocalDate;
import org.springframework.data.annotation.Id;

/** Foreign keys are plain ids: createdBy -> user_account. */
public record ObservationTemplate(
        @Id Integer templateId,
        Integer createdBy,
        String templateName,
        Integer version,
        LocalDate createdDate,
        Boolean activeStatus) {}
