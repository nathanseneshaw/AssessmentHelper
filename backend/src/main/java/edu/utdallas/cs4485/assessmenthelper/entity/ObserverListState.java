package edu.utdallas.cs4485.assessmenthelper.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;

public record ObserverListState(
        @Id Integer signupId, LocalDate windowStart, LocalDate windowEnd, LocalDateTime releasedAt) {}
