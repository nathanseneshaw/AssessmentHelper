package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("message_template")
public record MessageTemplate(
        @Id Integer templateId, String name, String subject, String body, String templateType, Boolean activeStatus) {}
