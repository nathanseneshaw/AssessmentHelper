package edu.utdallas.cs4485.assessmenthelper.entity;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** Foreign keys are plain ids: signupId -> evaluation_signup, observerId -> observer. */
@Table("observer_request")
public record ObserverRequest(
        @Id Integer requestId,
        Integer signupId,
        Integer observerId,
        LocalDateTime requestDate,
        LocalDateTime expirationDate,
        LocalDateTime responseDate,
        String status) {}
