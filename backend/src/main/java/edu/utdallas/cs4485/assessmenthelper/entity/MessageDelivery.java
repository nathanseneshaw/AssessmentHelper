package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;

/** Foreign keys are plain ids: messageId -> message, recipientUserId -> user_account. */
@Table("message_delivery")
public record MessageDelivery(
        @Id Integer deliveryId,
        Integer messageId,
        Integer recipientUserId,
        String channel,
        String deliveryStatus,
        LocalDateTime sentAt,
        LocalDateTime deliveredAt,
        LocalDateTime readAt) {
}
