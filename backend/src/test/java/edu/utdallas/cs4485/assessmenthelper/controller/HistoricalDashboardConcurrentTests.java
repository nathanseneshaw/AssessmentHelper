package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "historical.concurrent.test=true")
@AutoConfigureMockMvc
class HistoricalDashboardConcurrentTests {
    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    Clock clock;

    @Test
    void historicalDashboardLoadsAllEndpointsConcurrently() throws Exception {
        doReturn(Instant.parse("2026-11-19T12:00:00Z")).when(clock).instant();
        doReturn(ZoneOffset.UTC).when(clock).getZone();
        int cycle = jdbc.queryForObject("""
                SELECT c.cycle_id FROM evaluation_cycle c JOIN semester s ON s.semester_id = c.semester_id
                WHERE s.term = 'Spring' AND s.academic_year = 2026
                """, Integer.class);
        var principal = new AccountPrincipal(
                new CurrentUser(
                        1, 12, 1, "Kaia Clearbrook", "kaia.clearbrook@example.edu", List.of(Role.AC, Role.PROFESSOR)),
                null,
                true);
        var paths = List.of(
                "/api/ac-dashboard/cycles/" + cycle + "/metrics",
                "/api/ac-dashboard/cycles/" + cycle + "/observations",
                "/api/evaluation-eligibility/cycles/" + cycle + "/due",
                "/api/evaluation-eligibility/cycles/" + cycle + "/overdue",
                "/api/observer-eligibility/ac/cycles/" + cycle,
                "/api/cycles/" + cycle + "/missing-observations");
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(paths.size())) {
            var jobs = paths.stream()
                    .map(path -> pool.submit(() -> {
                        start.await();
                        var result = mvc.perform(get(path).with(user(principal)))
                                .andReturn()
                                .getResponse();
                        return path + " " + result.getStatus() + " " + result.getContentAsString();
                    }))
                    .toList();
            start.countDown();
            for (var job : jobs) {
                var result = job.get(30, TimeUnit.SECONDS);
                assertThat(result).contains(" 200 ");
                if (result.contains("/metrics ")) {
                    var metrics = new tools.jackson.databind.json.JsonMapper()
                            .readTree(result.substring(result.indexOf('{')));
                    assertThat(metrics.path("observerUtilization").path("total").asInt())
                            .isEqualTo(25);
                    assertThat(metrics.path("eligibilityAccuracy")
                                    .path("incorrect")
                                    .path("total")
                                    .asInt())
                            .isGreaterThan(0);
                }
            }
        }
    }
}
