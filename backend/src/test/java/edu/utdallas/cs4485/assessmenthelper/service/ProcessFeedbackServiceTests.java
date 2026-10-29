package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import edu.utdallas.cs4485.assessmenthelper.entity.*;
import edu.utdallas.cs4485.assessmenthelper.security.*;
import java.time.*;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

@DataJdbcTest
class ProcessFeedbackServiceTests {
    @Autowired
    JdbcAggregateTemplate entities;

    @Autowired
    JdbcTemplate db;

    private ProcessFeedbackService service;
    private Clock clock;
    private AccountPrincipal principal;
    private int cycleId;
    private int professorId;

    @BeforeEach
    void setup() {
        clock = mock(Clock.class);
        when(clock.getZone()).thenReturn(ZoneId.of("America/Chicago"));
        today("2026-11-02");
        service = new ProcessFeedbackService(db, clock);
        var school = entities.insert(new School(null, "Feedback School"));
        var department = entities.insert(new Department(null, school.schoolId(), "Feedback Department", "FD"));
        var professor = entities.insert(new Professor(
                null, department.departmentId(), "FULL", "Test", "Faculty", "feedback@test.edu", null, true));
        professorId = professor.professorId();
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
    void windowUsesChicagoDatesAndIncludesTheFeedbackDeadline() {
        today("2026-11-01");
        assertThat(status()).isEqualTo("NOT_OPEN");
        assertThatThrownBy(() -> service.submit(cycleId, answers(), principal)).isInstanceOf(ConflictException.class);
        today("2026-11-02");
        assertThat(status()).isEqualTo("AVAILABLE");
        today("2026-11-10");
        assertThat(status()).isEqualTo("AVAILABLE");
        today("2026-11-11");
        assertThat(status()).isEqualTo("CLOSED");
        assertThatThrownBy(() -> service.submit(cycleId, answers(), principal)).isInstanceOf(ConflictException.class);
    }

    @Test
    void savesAllAnswersOncePerCycleAndProfessor() {
        assertThat(service.submit(cycleId, answers(), principal).status()).isEqualTo("SUBMITTED");
        var row = db.queryForMap(
                "SELECT * FROM process_feedback WHERE cycle_id=? AND professor_id=?", cycleId, professorId);
        assertThat(row.get("CLARITY_RATING")).isEqualTo(1);
        assertThat(row.get("SCHEDULING_RATING")).isEqualTo(2);
        assertThat(row.get("COMMUNICATION_RATING")).isEqualTo(3);
        assertThat(row.get("ORGANIZATION_RATING")).isEqualTo(4);
        assertThat(row.get("USEFULNESS_RATING")).isEqualTo(5);
        assertThat(row.get("OVERALL_RATING")).isEqualTo(4);
        assertThat(row.get("OBSERVEE_COMMENT")).isEqualTo("Useful process");
        assertThat(status()).isEqualTo("SUBMITTED");
        var saved = service.surveys(principal).stream()
                .filter(s -> s.cycleId() == cycleId)
                .findFirst()
                .orElseThrow();
        assertThat(saved.ratings()).containsExactly(1, 2, 3, 4, 5, 4);
        assertThat(saved.comments()).isEqualTo("Useful process");
        assertThat(saved.submittedDate()).isEqualTo(LocalDate.parse("2026-11-02"));
        today("2026-12-01");
        assertThat(status()).isEqualTo("SUBMITTED");
        assertThatThrownBy(() -> service.submit(cycleId, answers(), principal)).isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsIncompleteInvalidAndOversizedAnswers() {
        for (List<Integer> ratings : List.of(
                List.of(1, 2),
                List.of(0, 2, 3, 4, 5, 1),
                List.of(6, 2, 3, 4, 5, 1),
                Arrays.asList(1, 2, null, 4, 5, 1))) {
            assertThatThrownBy(() ->
                            service.submit(cycleId, new ProcessFeedbackService.Submission(ratings, null), principal))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> service.submit(
                        cycleId,
                        new ProcessFeedbackService.Submission(answers().ratings(), "x".repeat(4001)),
                        principal))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(status()).isEqualTo("AVAILABLE");
    }

    @Test
    void refusesOtherCyclesAndMissingFacultyProfiles() {
        assertThatThrownBy(() -> service.submit(-1, answers(), principal)).isInstanceOf(AccessDeniedException.class);
        var noProfile = new AccountPrincipal(
                new CurrentUser(2, null, 1, "Other", "other@test.edu", List.of(Role.AC)), "", true);
        assertThatThrownBy(() -> service.surveys(noProfile)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void missingOrInvalidDeadlinesDoNotOpenSurvey() {
        db.update("UPDATE evaluation_cycle SET feedback_deadline=NULL WHERE cycle_id=?", cycleId);
        assertThat(status()).isEqualTo("UNCONFIGURED");
        assertThatThrownBy(() -> service.submit(cycleId, answers(), principal)).isInstanceOf(ConflictException.class);
    }
}
