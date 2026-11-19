package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.utdallas.cs4485.assessmenthelper.repository.SemesterRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.UserAccountRepository;
import edu.utdallas.cs4485.assessmenthelper.security.DemoTabSessions;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;

class DemoStateServiceTests {
    @Test
    void overlappingMovesEachLandOnTheRequestedDate() throws Exception {
        DemoTime time = new DemoTime();
        Clock clock = time.shift(Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"), ZoneOffset.UTC));
        ObserverEligibilityService eligibility = mock(ObserverEligibilityService.class);
        DemoStateService state = new DemoStateService(
                time,
                clock,
                mock(ObserverRequestService.class),
                eligibility,
                mock(SemesterRepository.class),
                mock(UserAccountRepository.class),
                mock(DemoTabSessions.class),
                mock(DataSource.class),
                mock(DataSourceScriptDatabaseInitializer.class));
        var firstValidating = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        when(eligibility.cycle())
                .thenAnswer(call -> {
                    firstValidating.countDown();
                    assertThat(releaseFirst.await(5, TimeUnit.SECONDS)).isTrue();
                    throw new ConflictException("No active cycle");
                })
                .thenThrow(new ConflictException("No active cycle"));

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
