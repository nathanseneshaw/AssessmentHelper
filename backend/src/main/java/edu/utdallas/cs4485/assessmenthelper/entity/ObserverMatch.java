package edu.utdallas.cs4485.assessmenthelper.entity;

import java.time.LocalDate;
import org.springframework.data.annotation.Id;

/** Foreign keys are plain ids: signupId -> evaluation_signup, observerId -> observer. */
public record ObserverMatch(
        @Id Integer matchId, Integer signupId, Integer observerId, LocalDate generatedDate, String matchStatus) {}
