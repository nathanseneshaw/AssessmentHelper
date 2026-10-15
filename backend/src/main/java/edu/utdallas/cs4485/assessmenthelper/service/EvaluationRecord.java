package edu.utdallas.cs4485.assessmenthelper.service;

import java.time.LocalDateTime;

public record EvaluationRecord(
        Integer cycleId,
        String term,
        Integer academicYear,
        String status,
        int observationCount,
        LocalDateTime lastObservationCompletedAt) {}
