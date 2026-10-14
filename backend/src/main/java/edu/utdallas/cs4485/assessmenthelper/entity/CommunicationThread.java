package edu.utdallas.cs4485.assessmenthelper.entity;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** Foreign keys are plain ids: cycleId -> evaluation_cycle, eligibilityId -> evaluation_eligibility, signupId -> evaluation_signup, requestId -> observer_request. */
@Table("communication_thread")
public record CommunicationThread(
        @Id Integer threadId,
        Integer cycleId,
        Integer eligibilityId,
        Integer signupId,
        Integer requestId,
        String threadType,
        String subject,
        String status,
        LocalDateTime createdAt,
        LocalDateTime lastActivityAt) {}
