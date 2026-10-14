package edu.utdallas.cs4485.assessmenthelper.entity;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** Foreign keys are plain ids: threadId -> communication_thread, templateId -> message_template, senderUserId -> user_account, replyToMessageId -> message. */
@Table("message")
public record Message(
        @Id Integer messageId,
        Integer threadId,
        Integer templateId,
        Integer senderUserId,
        Integer replyToMessageId,
        String messageType,
        String subject,
        String messageBody,
        LocalDateTime createdAt) {}
