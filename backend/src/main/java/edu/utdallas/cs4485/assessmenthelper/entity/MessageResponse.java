package edu.utdallas.cs4485.assessmenthelper.entity;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** Foreign keys are plain ids: messageId -> message, userId -> user_account. */
@Table("message_response")
public record MessageResponse(
        @Id Integer responseId,
        Integer messageId,
        Integer userId,
        String responseType,
        String responseValue,
        LocalDateTime respondedAt) {}
