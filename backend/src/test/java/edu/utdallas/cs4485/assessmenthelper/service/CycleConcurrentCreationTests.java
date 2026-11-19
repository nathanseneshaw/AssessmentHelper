package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "cycle.concurrent.test=true")
class CycleConcurrentCreationTests {
    @Autowired
    CycleSetupService service;

    @MockitoBean
    Clock clock;

    @Test
    void onlyOneCycleCanBeCreatedForTheSameSemesterConcurrently() throws Exception {
        doReturn(Instant.parse("2026-11-19T12:00:00Z")).when(clock).instant();
        doReturn(ZoneOffset.UTC).when(clock).getZone();
        var details = new CycleSetupService.Setup(
                "Spring",
                2027,
                LocalDate.parse("2027-01-19"),
                LocalDate.parse("2027-05-14"),
                LocalDate.parse("2027-02-10"),
                LocalDate.parse("2027-04-20"),
                LocalDate.parse("2027-05-10"));
        byte[] professors = ("email,first_name,last_name,department_code,rank,hire_date,active\n"
                        + "concurrent.cycle@example.edu,Concurrent,Cycle,CS,ASSISTANT,2024-08-01,true\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] sections = ("course_prefix,course_number,course_name,course_level,department_code,"
                        + "term,year,section,days,start_time,end_time,location,instructor_email\n"
                        + "CS,4390,Networks,,,Spring,2027,001,TR,10:00,11:15,,concurrent.cycle@example.edu\n")
                .getBytes(StandardCharsets.UTF_8);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var jobs = java.util.stream.IntStream.range(0, 2)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await();
                        try {
                            service.create(details, professors, sections);
                            return true;
                        } catch (IllegalArgumentException expected) {
                            return false;
                        }
                    }))
                    .toList();
            start.countDown();
            assertThat(List.of(
                            jobs.get(0).get(15, TimeUnit.SECONDS), jobs.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
    }
}
