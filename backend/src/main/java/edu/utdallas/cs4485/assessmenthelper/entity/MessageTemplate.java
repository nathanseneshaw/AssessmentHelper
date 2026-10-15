package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;

public record MessageTemplate(
        @Id Integer templateId, String name, String subject, String body, String templateType, Boolean activeStatus) {}
