package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationCycle;
import edu.utdallas.cs4485.assessmenthelper.entity.Semester;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationCycleRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.SemesterRepository;
import edu.utdallas.cs4485.assessmenthelper.service.CycleReadinessService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommitteeCyclesControllerTests {
    @Test
    void classifiesCyclesBySemesterAndAllowsGaps() {
        var cycles = mock(EvaluationCycleRepository.class);
        var semesters = mock(SemesterRepository.class);
        var clock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);
        when(semesters.findAll())
                .thenReturn(List.of(
                        new Semester(1, "Spring", 2026, LocalDate.parse("2026-01-12"), LocalDate.parse("2026-05-15")),
                        new Semester(2, "Fall", 2026, LocalDate.parse("2026-08-24"), LocalDate.parse("2026-12-15"))));
        when(cycles.findAll())
                .thenReturn(List.of(
                        new EvaluationCycle(1, 1, null, null, null, "OPEN"),
                        new EvaluationCycle(2, 2, null, null, null, "OPEN")));

        var result = new CommitteeCyclesController(cycles, semesters, clock, null, null).list();
        assertThat(result)
                .extracting(CommitteeCyclesController.CycleOption::period)
                .containsExactly("UPCOMING", "PAST");
    }

    @Test
    void semesterStartAndEndAreBothCurrent() {
        var cycles = mock(EvaluationCycleRepository.class);
        var semesters = mock(SemesterRepository.class);
        when(semesters.findAll())
                .thenReturn(List.of(
                        new Semester(1, "Fall", 2026, LocalDate.parse("2026-08-24"), LocalDate.parse("2026-12-16"))));
        when(cycles.findAll()).thenReturn(List.of(new EvaluationCycle(1, 1, null, null, null, "OPEN")));
        var readiness = mock(CycleReadinessService.class);
        when(readiness.check(1)).thenReturn(new CycleReadinessService.Readiness("READY", null));

        for (String date : List.of("2026-08-24T12:00:00Z", "2026-12-16T12:00:00Z")) {
            var clock = Clock.fixed(Instant.parse(date), ZoneOffset.UTC);
            assertThat(new CommitteeCyclesController(cycles, semesters, clock, null, readiness).list())
                    .extracting(CommitteeCyclesController.CycleOption::period)
                    .containsExactly("CURRENT");
        }
    }
}
