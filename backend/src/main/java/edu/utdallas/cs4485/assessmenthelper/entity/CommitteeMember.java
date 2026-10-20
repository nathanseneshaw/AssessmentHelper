package edu.utdallas.cs4485.assessmenthelper.entity;

import java.time.LocalDate;
import org.springframework.data.annotation.Id;

/** Foreign keys are plain ids: professorId -> professor (unique, one row per professor). */
public record CommitteeMember(
        @Id Integer committeeMemberId,
        Integer professorId,
        LocalDate startDate,
        LocalDate endDate,
        Boolean activeStatus) {}
