package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
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

@SpringBootTest(properties = "cycle.activation.test=true")
class CycleActivationTests {
    @Autowired
    CycleSetupService setup;

    @Autowired
    CycleReadinessService readiness;

    @Autowired
    ProfessorRepository professors;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    Clock clock;

    @Test
    void readyCycleActivatesRosterAfterSavingPreviousDashboard() {
        doReturn(ZoneOffset.UTC).when(clock).getZone();
        doReturn(Instant.parse("2026-11-19T12:00:00Z")).when(clock).instant();
        var previous =
                professors.findByEmailIgnoreCase("kaia.clearbrook@example.edu").orElseThrow();
        var details = new CycleSetupService.Setup(
                "Spring",
                2027,
                LocalDate.parse("2027-01-19"),
                LocalDate.parse("2027-05-14"),
                LocalDate.parse("2027-02-10"),
                LocalDate.parse("2027-04-20"),
                LocalDate.parse("2027-05-10"));
        byte[] professorFile = ("email,first_name,last_name,department_code,rank,hire_date,active\n"
                        + "kaia.clearbrook@example.edu,Future,Clearbrook,CS,ASSISTANT,2024-08-01,true\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] sectionFile = ("course_prefix,course_number,course_name,course_level,department_code,"
                        + "term,year,section,days,start_time,end_time,location,instructor_email\n"
                        + "CS,4390,Networks,,,Spring,2027,001,TR,10:00,11:15,,kaia.clearbrook@example.edu\n")
                .getBytes(StandardCharsets.UTF_8);
        var cycle = setup.create(details, professorFile, sectionFile);
        assertThat(professors.findById(previous.professorId())).contains(previous);
        doReturn(Instant.parse("2027-01-19T12:00:00Z")).when(clock).instant();
        assertThat(readiness.check(cycle.cycleId()).state()).isEqualTo("READY");
        assertThat(professors.findById(previous.professorId()).orElseThrow().firstName())
                .isEqualTo("Future");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cycle_dashboard_snapshot WHERE cycle_id=1", Integer.class))
                .isEqualTo(1);
    }
}
