package edu.utdallas.cs4485.assessmenthelper.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** Foreign keys are plain ids: professorId -> professor. */
@Table("user_account")
public record UserAccount(
        @Id Integer userId,
        Integer professorId,
        String username,
        String email,
        String passwordHash,
        @Column("is_active") Boolean active) {}
