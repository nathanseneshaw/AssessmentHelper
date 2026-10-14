package edu.utdallas.cs4485.assessmenthelper.entity;

import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** Foreign keys are plain ids: requestId -> observer_request. */
@Table("observer_request_time")
public record ObserverRequestTime(
        @Id Integer requestTimeId,
        Integer requestId,
        LocalDate proposedDate,
        LocalTime startTime,
        LocalTime endTime,
        String status) {}
