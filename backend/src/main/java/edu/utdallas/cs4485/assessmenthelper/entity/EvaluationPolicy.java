package edu.utdallas.cs4485.assessmenthelper.entity;

import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * The rank name is the primary key and is assigned by hand, so the version field is how
 * Spring Data JDBC tells a new policy (version null) from an existing one.
 */
@Table("evaluation_policy")
public record EvaluationPolicy(
        @Id String professorRank,
        Integer evaluationIntervalYears,
        Boolean activeStatus,
        LocalDate madeDate,
        LocalDate effectiveDate,
        @Version Long version) {}
