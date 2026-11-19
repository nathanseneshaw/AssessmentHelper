package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationCycleRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.SemesterRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "cycle.rollback.test=true")
class CycleSetupRollbackTests {
    @Autowired
    CycleSetupService setup;

    @Autowired
    ProfessorRepository professors;

    @Autowired
    SemesterRepository semesters;

    @Autowired
    EvaluationCycleRepository cycles;

    @MockitoBean
    Clock clock;

    @Test
    void invalidSectionRollsBackProfessorAndCycleCreation() {
        doReturn(Instant.parse("2026-11-19T12:00:00Z")).when(clock).instant();
        doReturn(ZoneOffset.UTC).when(clock).getZone();
        var semester = semesters
                .findFirstByTermIgnoreCaseAndAcademicYearOrderBySemesterId("Spring", 2027)
                .orElseThrow();
        var details = new CycleSetupService.Setup(
                "Spring",
                2027,
                LocalDate.parse("2027-01-19"),
                LocalDate.parse("2027-05-14"),
                LocalDate.parse("2027-02-10"),
                LocalDate.parse("2027-04-20"),
                LocalDate.parse("2027-05-10"));
        byte[] professorFile = ("email,first_name,last_name,department_code,rank,hire_date,active\n"
                        + "rollback.check@example.edu,Rollback,Check,CS,ASSISTANT,2024-08-01,true\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] sectionFile = ("course_prefix,course_number,course_name,course_level,department_code,"
                        + "term,year,section,days,start_time,end_time,location,instructor_email\n"
                        + "CS,4390,Networks,,,Spring,2027,001,TR,invalid,11:15,,rollback.check@example.edu\n")
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> setup.create(details, professorFile, sectionFile))
                .isInstanceOf(CycleImportException.class)
                .hasMessageContaining("sections CSV");
        assertThat(professors.findByEmailIgnoreCase("rollback.check@example.edu"))
                .isEmpty();
        assertThat(semesters.findById(semester.semesterId())).contains(semester);
        assertThat(cycles.findAll()).noneMatch(cycle -> cycle.semesterId().equals(semester.semesterId()));
    }

    @Test
    void invalidReplacementKeepsTheExistingRosterAndAssignments() {
        doReturn(Instant.parse("2026-11-19T12:00:00Z")).when(clock).instant();
        doReturn(ZoneOffset.UTC).when(clock).getZone();
        var details = new CycleSetupService.Setup(
                "Summer",
                2027,
                LocalDate.parse("2027-05-26"),
                LocalDate.parse("2027-08-12"),
                LocalDate.parse("2027-06-10"),
                LocalDate.parse("2027-07-20"),
                LocalDate.parse("2027-08-10"));
        String professorHeader = "email,first_name,last_name,department_code,rank,hire_date,active\n";
        String sectionHeader = "course_prefix,course_number,course_name,course_level,department_code,"
                + "term,year,section,days,start_time,end_time,location,instructor_email\n";
        byte[] oldProfessors = (professorHeader + "old.replace@example.edu,Old,Replace,CS,ASSISTANT,2024-08-01,true\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] oldSections = (sectionHeader
                        + "CS,4390,Networks,,,Summer,2027,001,TR,10:00,11:15,,old.replace@example.edu\n")
                .getBytes(StandardCharsets.UTF_8);
        var cycle = setup.create(details, oldProfessors, oldSections);
        byte[] newProfessors = (professorHeader + "new.replace@example.edu,New,Replace,CS,ASSISTANT,2024-08-01,true\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] newSections = (sectionHeader
                        + "CS,4390,Networks,,,Summer,2027,001,TR,invalid,11:15,,new.replace@example.edu\n")
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> setup.replaceFiles(cycle.cycleId(), newProfessors, newSections))
                .isInstanceOf(CycleImportException.class);
        assertThat(cycles.findRosterProfessors(cycle.cycleId()))
                .extracting(p -> p.email())
                .containsExactly("old.replace@example.edu");
        assertThat(professors.findByEmailIgnoreCase("new.replace@example.edu")).isEmpty();
    }
}
