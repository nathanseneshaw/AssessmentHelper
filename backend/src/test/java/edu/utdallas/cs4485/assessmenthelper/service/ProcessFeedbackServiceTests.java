package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import edu.utdallas.cs4485.assessmenthelper.entity.*;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationCycleRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserveeRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProcessFeedbackRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.SemesterRepository;
import edu.utdallas.cs4485.assessmenthelper.security.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

@DataJdbcTest
class ProcessFeedbackServiceTests {
    @Autowired
    JdbcAggregateTemplate entities;

    @Autowired
    ProfessorRepository professors;

    @Autowired
    ObserveeRepository observees;

    @Autowired
    EvaluationCycleRepository cycles;

    @Autowired
    SemesterRepository semesters;

    @Autowired
    ProcessFeedbackRepository feedback;

    private ProcessFeedbackService service;
    private Clock clock;
    private AccountPrincipal principal;
    private int cycleId;

    @BeforeEach
    void setup() {
        clock = mock(Clock.class);
        when(clock.getZone()).thenReturn(ZoneId.of("America/Chicago"));
        today("2026-11-02");
        service = new ProcessFeedbackService(cycles, professors, feedback, clock);
        var school = entities.insert(new School(null, "Feedback School"));
        var department = entities.insert(new Department(null, school.schoolId(), "Feedback Department", "FD"));
        var professor = entities.insert(new Professor(
                null, department.departmentId(), "FULL", "Test", "Faculty", "feedback@test.edu", null, true));
        int professorId = professor.professorId();
        var semester = entities.insert(
                new Semester(null, "Fall", 2026, LocalDate.parse("2026-08-01"), LocalDate.parse("2026-12-31")));
        var cycle = entities.insert(new EvaluationCycle(
                null,
                semester.semesterId(),
                LocalDate.parse("2026-10-01"),
                LocalDate.parse("2026-11-01"),
                LocalDate.parse("2026-11-10"),
                "OPEN"));
        cycleId = cycle.cycleId();
        entities.insert(new Observee(null, professorId, cycleId, "ACTIVE"));
        principal = new AccountPrincipal(
                new CurrentUser(
                        1,
                        professorId,
                        school.schoolId(),
                        "Test Faculty",
                        "feedback@test.edu",
                        List.of(Role.PROFESSOR)),
                "",
                true);
    }

    private void today(String date) {
        when(clock.instant())
                .thenReturn(LocalDate.parse(date)
                        .atStartOfDay(ZoneId.of("America/Chicago"))
                        .toInstant());
    }

    private String status() {
        return service.surveys(principal).stream()
                .filter(s -> s.cycleId() == cycleId)
                .findFirst()
                .orElseThrow()
                .status();
    }

    private ProcessFeedbackService.Submission answers() {
        return new ProcessFeedbackService.Submission(List.of(1, 2, 3, 4, 5, 4), " Useful process ");
    }

    @Test
    void theSurveyIsOpenOnlyBetweenTheObservationAndFeedbackDeadlines() {
        today("2026-11-01");
        assertThat(status()).isEqualTo("NOT_OPEN");
        today("2026-11-05");
        assertThat(status()).isEqualTo("AVAILABLE");
        today("2026-11-11");
        assertThat(status()).isEqualTo("CLOSED");
        assertThatThrownBy(() -> service.submit(cycleId, answers(), principal)).isInstanceOf(ConflictException.class);
    }

    @Test
    void savesAllAnswersOncePerCycleAndProfessor() {
        assertThat(service.submit(cycleId, answers(), principal).status()).isEqualTo("SUBMITTED");
        var saved = service.surveys(principal).stream()
                .filter(s -> s.cycleId() == cycleId)
                .findFirst()
                .orElseThrow();
        assertThat(saved.status()).isEqualTo("SUBMITTED");
        assertThat(saved.ratings()).containsExactly(1, 2, 3, 4, 5, 4);
        assertThat(saved.comments()).isEqualTo("Useful process");
        assertThatThrownBy(() -> service.submit(cycleId, answers(), principal)).isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsARatingOutsideOneToFive() {
        assertThatThrownBy(() -> service.submit(
                        cycleId, new ProcessFeedbackService.Submission(List.of(6, 2, 3, 4, 5, 1), null), principal))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(status()).isEqualTo("AVAILABLE");
    }
}
