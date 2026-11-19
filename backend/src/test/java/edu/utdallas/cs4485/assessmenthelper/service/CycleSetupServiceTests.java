package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import edu.utdallas.cs4485.assessmenthelper.entity.Observee;
import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationCycleRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserveeRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CycleSetupServiceTests {
    @Autowired
    CycleSetupService service;

    @Autowired
    EvaluationCycleRepository cycles;

    @Autowired
    ProfessorRepository professors;

    @Autowired
    EvaluationEligibilityService eligibility;

    @Autowired
    AcDashboardService dashboard;

    @Autowired
    ObserveeRepository observees;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void fixedDate() {
        doReturn(Instant.parse("2026-11-19T12:00:00Z")).when(clock).instant();
        doReturn(ZoneOffset.UTC).when(clock).getZone();
    }

    private final CycleSetupService.Setup setup = new CycleSetupService.Setup(
            "Spring",
            2027,
            LocalDate.parse("2027-01-19"),
            LocalDate.parse("2027-05-14"),
            LocalDate.parse("2027-02-10"),
            LocalDate.parse("2027-04-20"),
            LocalDate.parse("2027-05-10"));
    private final byte[] professorCsv = ("email,first_name,last_name,department_code,rank,hire_date,active\n"
                    + "cycle.setup@example.edu,Cycle,Setup,CS,ASSISTANT,2024-08-01,true\n")
            .getBytes(StandardCharsets.UTF_8);
    private final byte[] sectionCsv = ("course_prefix,course_number,course_name,course_level,department_code,"
                    + "term,year,section,days,start_time,end_time,location,instructor_email\n"
                    + "CS,4390,Networks,,,Spring,2027,001,TR,10:00,11:15,,cycle.setup@example.edu\n")
            .getBytes(StandardCharsets.UTF_8);

    @Test
    void createsCycleAndSemesterRosterUsingExistingImports() {
        var cycle = service.create(setup, professorCsv, sectionCsv);
        var professor =
                professors.findByEmailIgnoreCase("cycle.setup@example.edu").orElseThrow();
        assertThat(cycles.findRosterProfessorIds(cycle.cycleId())).containsExactly(professor.professorId());
    }

    @Test
    void providedSpring2027SampleFilesCreateACycle() throws Exception {
        var cycle = service.create(
                setup,
                java.nio.file.Files.readAllBytes(java.nio.file.Path.of("../sample-data/spring-2027-professors.csv")),
                java.nio.file.Files.readAllBytes(java.nio.file.Path.of("../sample-data/spring-2027-sections.csv")));
        assertThat(cycles.findRosterProfessorIds(cycle.cycleId())).hasSize(6);
    }

    @Test
    void rejectsSectionsFromAnotherSemester() {
        byte[] wrongSemester = new String(sectionCsv, StandardCharsets.UTF_8)
                .replace("Spring,2027", "Fall,2026")
                .getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> service.create(setup, professorCsv, wrongSemester))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("selected semester");
        assertThat(professors.findByEmailIgnoreCase("cycle.setup@example.edu")).isEmpty();
    }

    @Test
    void rejectsAProfessorWithoutAnUploadedTeachingAssignment() {
        byte[] noInstructor = new String(sectionCsv, StandardCharsets.UTF_8)
                .replace(",cycle.setup@example.edu", ",")
                .getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> service.create(setup, professorCsv, noInstructor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has no section");
    }

    @Test
    void cannotCreateAfterTheSemesterHasStarted() {
        doReturn(Instant.parse("2027-01-19T12:00:00Z")).when(clock).instant();
        assertThatThrownBy(() -> service.create(setup, professorCsv, sectionCsv))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("before its semester starts");
    }

    @Test
    void offersTheNextSixSpringSummerAndFallSemesters() {
        assertThat(service.upcomingSemesters())
                .extracting(option -> option.term() + " " + option.year())
                .containsExactly("Spring 2027", "Summer 2027", "Fall 2027", "Spring 2028", "Summer 2028", "Fall 2028");
    }

    @Test
    void overdueRosterMemberStaysOverdueWhenAnotherIntervalFallsInTheSemester() {
        byte[] januaryHire = new String(professorCsv, StandardCharsets.UTF_8)
                .replace("2024-08-01", "2024-01-25")
                .getBytes(StandardCharsets.UTF_8);
        var cycle = service.create(setup, januaryHire, sectionCsv);
        doReturn(Instant.parse("2027-02-01T12:00:00Z")).when(clock).instant();
        var overdue = eligibility.findOverdue();
        assertThat(overdue.cycleId()).isEqualTo(cycle.cycleId());
        assertThat(overdue.faculty())
                .extracting(EvaluationEligibilityService.Faculty::email)
                .containsExactly("cycle.setup@example.edu");
    }

    @Test
    void eligibilityUsesTheProfessorInformationSavedWithTheCycle() {
        service.create(setup, professorCsv, sectionCsv);
        var original =
                professors.findByEmailIgnoreCase("cycle.setup@example.edu").orElseThrow();
        professors.save(new Professor(
                original.professorId(),
                original.departmentId(),
                original.professorRank(),
                "Changed",
                original.lastName(),
                original.email(),
                original.hireDate(),
                false));
        doReturn(Instant.parse("2027-02-01T12:00:00Z")).when(clock).instant();

        assertThat(eligibility.findOverdue().faculty())
                .extracting(EvaluationEligibilityService.Faculty::name)
                .containsExactly("Cycle Setup");
    }

    @Test
    void eligibilityCanBeReadForAFutureCycleBeforeItsSemesterStarts() {
        var cycle = service.create(setup, professorCsv, sectionCsv);
        var queue = eligibility.findAll(cycle.cycleId());
        assertThat(queue.cycleId()).isEqualTo(cycle.cycleId());
        assertThat(queue.faculty())
                .extracting(EvaluationEligibilityService.Faculty::email)
                .containsExactly("cycle.setup@example.edu");
        assertThat(dashboard.metrics(cycle.cycleId()).cycleId()).isEqualTo(cycle.cycleId());
    }

    @Test
    void futureRosterChangesDoNotOverwriteExistingProfessorProfiles() {
        var existing =
                professors.findByEmailIgnoreCase("kaia.clearbrook@example.edu").orElseThrow();
        byte[] changedProfessor = new String(professorCsv, StandardCharsets.UTF_8)
                .replace("cycle.setup@example.edu,Cycle,Setup", "kaia.clearbrook@example.edu,Future,Name")
                .getBytes(StandardCharsets.UTF_8);
        byte[] changedSection = new String(sectionCsv, StandardCharsets.UTF_8)
                .replace("cycle.setup@example.edu", "kaia.clearbrook@example.edu")
                .getBytes(StandardCharsets.UTF_8);
        var cycle = service.create(setup, changedProfessor, changedSection);
        assertThat(professors.findById(existing.professorId())).contains(existing);
        assertThat(cycles.findRosterProfessors(cycle.cycleId()))
                .extracting(Professor::firstName)
                .containsExactly("Future");
    }

    @Test
    void aNewProfessorInAFutureCycleDoesNotAppearInTheCurrentEligibilityList() {
        service.create(setup, professorCsv, sectionCsv);
        assertThat(eligibility.findAll().faculty())
                .extracting(EvaluationEligibilityService.Faculty::email)
                .doesNotContain("cycle.setup@example.edu");
    }

    @Test
    void futureDatesCanBeEditedOnlyBeforeTheSemesterStarts() {
        var cycle = service.create(setup, professorCsv, sectionCsv);
        var changed = new CycleSetupService.Setup(
                "Spring",
                2027,
                setup.startDate(),
                setup.endDate(),
                LocalDate.parse("2027-02-12"),
                setup.observationDeadline(),
                setup.feedbackDeadline());
        assertThat(service.updateDates(cycle.cycleId(), changed).signupDeadline())
                .isEqualTo(LocalDate.parse("2027-02-12"));
        doReturn(Instant.parse("2027-01-19T12:00:00Z")).when(clock).instant();
        assertThatThrownBy(() -> service.updateDates(cycle.cycleId(), changed))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cannot be edited");
        assertThatThrownBy(() -> service.replaceFiles(cycle.cycleId(), professorCsv, sectionCsv))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cannot be edited");
    }

    @Test
    void semesterDatesCannotOverlapAnotherSemester() {
        var overlapping = new CycleSetupService.Setup(
                "Spring",
                2027,
                LocalDate.parse("2027-01-19"),
                LocalDate.parse("2027-06-02"),
                LocalDate.parse("2027-02-10"),
                LocalDate.parse("2027-04-20"),
                LocalDate.parse("2027-05-10"));
        assertThatThrownBy(() -> service.create(overlapping, professorCsv, sectionCsv))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap");
    }

    @Test
    void replacingFutureFilesReplacesTheRosterAndTeachingAssignments() {
        var cycle = service.create(setup, professorCsv, sectionCsv);
        var previous =
                professors.findByEmailIgnoreCase("cycle.setup@example.edu").orElseThrow();
        byte[] nextProfessors = new String(professorCsv, StandardCharsets.UTF_8)
                .replace("cycle.setup@example.edu,Cycle,Setup", "new.roster@example.edu,New,Roster")
                .getBytes(StandardCharsets.UTF_8);
        byte[] nextSections = new String(sectionCsv, StandardCharsets.UTF_8)
                .replace("cycle.setup@example.edu", "new.roster@example.edu")
                .getBytes(StandardCharsets.UTF_8);
        service.replaceFiles(cycle.cycleId(), nextProfessors, nextSections);
        assertThat(cycles.findRosterProfessors(cycle.cycleId()))
                .extracting(Professor::email)
                .containsExactly("new.roster@example.edu");
        assertThat(cycles.findFutureOnlyProfessorIds(LocalDate.now(clock))).contains(previous.professorId());
        assertThat(eligibility.findAll().faculty())
                .extracting(EvaluationEligibilityService.Faculty::email)
                .doesNotContain("cycle.setup@example.edu", "new.roster@example.edu");
    }

    @Test
    void anUnfinishedEarlierEvaluationIsOverdueEvenWhenTheNormalDueDateIsLater() {
        byte[] recentHire = new String(professorCsv, StandardCharsets.UTF_8)
                .replace("ASSISTANT,2024-08-01", "ASSOCIATE,2026-08-01")
                .getBytes(StandardCharsets.UTF_8);
        var cycle = service.create(setup, recentHire, sectionCsv);
        var professor =
                professors.findByEmailIgnoreCase("cycle.setup@example.edu").orElseThrow();
        observees.save(new Observee(null, professor.professorId(), 1, "ACTIVE"));
        doReturn(Instant.parse("2027-02-01T12:00:00Z")).when(clock).instant();
        assertThat(eligibility.findOverdue(cycle.cycleId()).faculty())
                .extracting(EvaluationEligibilityService.Faculty::email)
                .containsExactly("cycle.setup@example.edu");
    }
}
