package edu.utdallas.cs4485.assessmenthelper.entity;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;

public record EvaluationDeferral(
        @Id Integer signupId,
        Integer professorId,
        Integer targetSemesterId,
        String reason,
        Integer deferredBy,
        LocalDateTime deferredAt) {}
