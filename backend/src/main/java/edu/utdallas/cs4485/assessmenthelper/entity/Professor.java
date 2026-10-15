package edu.utdallas.cs4485.assessmenthelper.entity;

import java.time.LocalDate;
import org.springframework.data.annotation.Id;

/** Foreign keys are plain ids: departmentId -> department, professorRank -> evaluation_policy. */
public record Professor(
        @Id Integer professorId,
        Integer departmentId,
        String professorRank,
        String firstName,
        String lastName,
        String email,
        LocalDate hireDate,
        Boolean activeStatus) {}
