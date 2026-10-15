package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;

/** Foreign keys are plain ids: professorId -> professor. */
public record UserAccount(
        @Id Integer userId,
        Integer professorId,
        String username,
        String email,
        String passwordHash,
        Boolean isActive) {}
