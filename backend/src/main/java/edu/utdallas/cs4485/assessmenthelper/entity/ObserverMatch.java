package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDate;

/** Foreign keys are plain ids: signupId -> evaluation_signup, observerId -> observer. */
@Table("observer_match")
public record ObserverMatch(
        @Id Integer matchId,
        Integer signupId,
        Integer observerId,
        LocalDate generatedDate,
        String matchStatus) {
}
