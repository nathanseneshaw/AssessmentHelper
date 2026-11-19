package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationCycleRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationCycleRepository.CycleSemesterRow;
import edu.utdallas.cs4485.assessmenthelper.repository.UserAccountRepository;
import edu.utdallas.cs4485.assessmenthelper.security.DemoTabSessions;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;

class DemoStateServiceTests {
    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

    private static DemoStateService state(DemoTime time, EvaluationCycleRepository cycles) {
        return new DemoStateService(
                time,
                time.shift(Clock.fixed(NOW, ZoneOffset.UTC)),
                mock(ObserverRequestService.class),
                cycles,
                mock(UserAccountRepository.class),
                mock(DemoTabSessions.class),
                mock(DataSource.class),
                mock(DataSourceScriptDatabaseInitializer.class));
    }

    @Test
    void movesOnlyIntoCurrentOrLaterSemestersWithACycle() {
        EvaluationCycleRepository cycles = mock(EvaluationCycleRepository.class);
        when(cycles.findCycleSemestersEndingOnOrAfter(LocalDate.of(2026, 11, 19)))
                .thenReturn(List.of(
                        new CycleSemesterRow("Fall", 2026, LocalDate.of(2026, 8, 24), LocalDate.of(2026, 12, 18)),
                        new CycleSemesterRow("Spring", 2027, LocalDate.of(2027, 1, 19), LocalDate.of(2027, 5, 14))));
        DemoStateService state = state(new DemoTime(), cycles);

        assertThat(state.moveTo(LocalDate.of(2027, 2, 1)).today()).isEqualTo(LocalDate.of(2027, 2, 1));
        assertThatThrownBy(() -> state.moveTo(LocalDate.of(2027, 1, 5))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> state.moveTo(LocalDate.of(2026, 5, 1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overlappingMovesEachLandOnTheRequestedDate() throws Exception {
        EvaluationCycleRepository cycles = mock(EvaluationCycleRepository.class);
        DemoStateService state = state(new DemoTime(), cycles);
        var firstValidating = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        when(cycles.findCycleSemestersEndingOnOrAfter(any()))
                .thenAnswer(call -> {
                    firstValidating.countDown();
                    assertThat(releaseFirst.await(5, TimeUnit.SECONDS)).isTrue();
                    return List.of();
                })
                .thenReturn(List.of());

        LocalDate firstDate = LocalDate.of(2026, 11, 24);
        LocalDate secondDate = LocalDate.of(2026, 11, 26);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> state.moveTo(firstDate));
            try {
                assertThat(firstValidating.await(5, TimeUnit.SECONDS)).isTrue();
                var secondStarted = new CountDownLatch(1);
                var second = executor.submit(() -> {
                    secondStarted.countDown();
                    return state.moveTo(secondDate);
                });
                assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                releaseFirst.countDown();
                assertThat(first.get(5, TimeUnit.SECONDS).today()).isEqualTo(firstDate);
                assertThat(second.get(5, TimeUnit.SECONDS).today()).isEqualTo(secondDate);
            } finally {
                releaseFirst.countDown();
            }
        }
        assertThat(state.date().today()).isEqualTo(secondDate);
    }
}
