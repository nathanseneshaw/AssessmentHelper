package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import edu.utdallas.cs4485.assessmenthelper.entity.*;
import edu.utdallas.cs4485.assessmenthelper.repository.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class OverdueEvaluationServiceTests {
    private final ProfessorRepository professors = mock(ProfessorRepository.class);
    private final EvaluationPolicyRepository policies = mock(EvaluationPolicyRepository.class);
    private final SemesterRepository semesters = mock(SemesterRepository.class);
    private final EvaluationCycleRepository cycles = mock(EvaluationCycleRepository.class);
    private final ObserveeRepository observees = mock(ObserveeRepository.class);
    private final EvaluationSignupRepository signups = mock(EvaluationSignupRepository.class);
    private final ObservationRepository observations = mock(ObservationRepository.class);
    private final EvaluationNotificationRepository notifications = mock(EvaluationNotificationRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T15:00:00Z"), ZoneId.of("America/Chicago"));
    private final OverdueEvaluationService service = new OverdueEvaluationService(
            professors, policies, semesters, cycles, observees, signups, observations, notifications, clock);

    @BeforeEach
    void setup() {
        when(semesters.findAll()).thenReturn(List.of(
                new Semester(1, "Fall", 2026, LocalDate.of(2026, 8, 24), LocalDate.of(2026, 12, 11)),
                new Semester(2, "Spring", 2025, LocalDate.of(2025, 1, 13), LocalDate.of(2025, 5, 9))));
        when(cycles.findAll()).thenReturn(List.of(new EvaluationCycle(1, 1, null, null, null, "OPEN"), new EvaluationCycle(2, 2, null, null, null, "CLOSED")));
        when(policies.findAll()).thenReturn(List.of(
                new EvaluationPolicy("ASSISTANT", 1, true, null, null, 0L),
                new EvaluationPolicy("ASSOCIATE", 2, true, null, null, 0L),
                new EvaluationPolicy("FULL", 2, true, null, null, 0L)));
        when(professors.findAll()).thenReturn(List.of(professor(1, "ASSISTANT", "2024-03-01", true)));
        when(observees.findAll()).thenReturn(List.of());
        when(signups.findAll()).thenReturn(List.of());
        when(observations.findAll()).thenReturn(List.of());
        when(notifications.findAll()).thenReturn(List.of());
    }

    @Test
    void usesRankIntervalsAndHireDateAndExcludesInactiveFaculty() {
        when(professors.findAll()).thenReturn(List.of(
                professor(1, "ASSISTANT", "2025-03-01", true),
                professor(2, "ASSOCIATE", "2025-03-01", true),
                professor(3, "FULL", "2023-03-01", true),
                professor(4, "ASSISTANT", "2020-03-01", false)));
        assertThat(service.findOverdue().faculty()).extracting(OverdueEvaluationService.Faculty::professorId).containsExactly(1, 3);
    }

    @Test
    void dueThisSemesterTakesPriorityEvenWithPreviouslyMissedEvaluations() {
        when(professors.findAll()).thenReturn(List.of(professor(1, "ASSISTANT", "2023-10-01", true)));
        assertThat(service.findOverdue().count()).isZero();
    }

    @Test
    void multipleMissedCyclesCountProfessorOnce() {
        when(observees.findAll()).thenReturn(List.of(new Observee(1, 1, 1, "ACTIVE"), new Observee(2, 1, 2, "ACTIVE")));
        assertThat(service.findOverdue().count()).isEqualTo(1);
    }

    @Test
    void onlyObserveeCompletionClearsOldObligationsAndResetsSchedule() {
        when(observees.findAll()).thenReturn(List.of(new Observee(1, 1, 1, "ACTIVE")));
        when(signups.findAll()).thenReturn(List.of(new EvaluationSignup(1, 1, 1, null, null)));
        when(observations.findAll()).thenReturn(List.of(new Observation(1, 1, 1, 1, null, null, "COMPLETED", true, true, null, null, LocalDateTime.of(2026, 10, 1, 10, 0))));
        assertThat(service.findOverdue().count()).isEqualTo(1);
        when(observees.findAll()).thenReturn(List.of(new Observee(1, 1, 1, "COMPLETED")));
        assertThat(service.findOverdue().count()).isZero();
    }

    @Test
    void notificationIsPersistedAndDoesNotClearOverdue() {
        when(notifications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var email = new OverdueEvaluationService.Email("p1@test.edu", "Reminder", "Edited message");
        var result = service.notifyProfessor(1, 1, email);
        assertThat(result.body()).isEqualTo("Edited message");
        assertThat(result.sentAt()).isEqualTo(LocalDateTime.of(2026, 10, 20, 10, 0));
        when(notifications.findAll()).thenReturn(List.of(result));
        var reloaded = service.findOverdue();
        assertThat(reloaded.count()).isEqualTo(1);
        assertThat(reloaded.faculty().getFirst().lastNotifiedAt()).isEqualTo(result.sentAt());
    }

    @Test
    void rejectsWrongRecipientOrStaleCycleWithoutSaving() {
        assertThatThrownBy(() -> service.notifyProfessor(1, 1, new OverdueEvaluationService.Email("other@test.edu", "Hi", "Body"))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.notifyProfessor(99, 1, new OverdueEvaluationService.Email("p1@test.edu", "Hi", "Body"))).isInstanceOf(ResponseStatusException.class);
        verify(notifications, never()).save(any());
    }

    @Test
    void reportsMissingCurrentSemesterAndMissingCompletionDate() {
        when(observees.findAll()).thenReturn(List.of(new Observee(1, 1, 1, "COMPLETED")));
        assertThatThrownBy(service::findOverdue).hasMessageContaining("missing its observation completion date");
        when(semesters.findAll()).thenReturn(List.of());
        assertThatThrownBy(service::findOverdue).hasMessageContaining("No unique semester");
    }

    private Professor professor(int id, String rank, String hireDate, boolean active) {
        return new Professor(id, 1, rank, "Professor", String.valueOf(id), "p" + id + "@test.edu", LocalDate.parse(hireDate), active);
    }
}
