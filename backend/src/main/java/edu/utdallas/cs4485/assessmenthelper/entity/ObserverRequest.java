package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;

/** Foreign keys are plain ids: signupId -> evaluation_signup, observerId -> observer. */
@Table("observer_request")
public record ObserverRequest(
        @Id Integer requestId,
        Integer signupId,
        Integer observerId,
        LocalDateTime requestDate,
        LocalDateTime expirationDate,
        LocalDateTime responseDate,
        String status) {
}
