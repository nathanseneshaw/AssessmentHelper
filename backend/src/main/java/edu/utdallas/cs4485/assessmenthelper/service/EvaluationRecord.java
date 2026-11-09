package edu.utdallas.cs4485.assessmenthelper.service;

import java.time.LocalDateTime;

public record EvaluationRecord(
        Integer cycleId,
        String term,
        Integer academicYear,
        String status,
        int observationCount,
        LocalDateTime lastObservationCompletedAt,

        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        Deferral deferral) {
    public record Deferral(String reason, String deferredBy, LocalDateTime deferredAt, String targetSemester) {}

    public EvaluationRecord(
            Integer cycleId,
            String term,
            Integer academicYear,
            String status,
            int observationCount,
            LocalDateTime lastObservationCompletedAt) {
        this(cycleId, term, academicYear, status, observationCount, lastObservationCompletedAt, null);
    }
}
