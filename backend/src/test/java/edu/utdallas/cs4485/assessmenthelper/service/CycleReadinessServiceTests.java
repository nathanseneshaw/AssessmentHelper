package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "cycle.readiness.test=true")
class CycleReadinessServiceTests {
    @Autowired
    CycleSetupService setup;

    @Autowired
    CycleReadinessService readiness;

    @Autowired
    CycleReadinessScheduler scheduler;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    Clock clock;

    @Test
    void failedReadinessIsPermanentAfterTheSemesterStarts() {
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
        byte[] professorFile = ("email,first_name,last_name,department_code,rank,hire_date,active\n"
                        + "readiness.check@example.edu,Readiness,Check,CS,ASSISTANT,2024-08-01,true\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] sectionFile = ("course_prefix,course_number,course_name,course_level,department_code,"
                        + "term,year,section,days,start_time,end_time,location,instructor_email\n"
                        + "CS,4390,Networks,,,Spring,2027,001,TR,10:00,11:15,,readiness.check@example.edu\n")
                .getBytes(StandardCharsets.UTF_8);
        var cycle = setup.create(details, professorFile, sectionFile);
        assertThat(readiness.check(cycle.cycleId()).state()).isEqualTo("PENDING");
        Integer professor = jdbc.queryForObject(
                "SELECT professor_id FROM professor WHERE email=?", Integer.class, "readiness.check@example.edu");
        Integer section = jdbc.queryForObject(
                "SELECT section_id FROM teaching_history WHERE professor_id=?", Integer.class, professor);
        jdbc.update("DELETE FROM teaching_history WHERE professor_id=? AND section_id=?", professor, section);
        doReturn(Instant.parse("2027-01-19T12:00:00Z")).when(clock).instant();
        scheduler.finalizeStartedCycles();
        assertThat(readiness.check(cycle.cycleId()).state()).isEqualTo("FAILED");
        jdbc.update("INSERT INTO teaching_history (professor_id, section_id) VALUES (?, ?)", professor, section);
        assertThat(readiness.check(cycle.cycleId()).state()).isEqualTo("FAILED");
    }
}
