package edu.utdallas.cs4485.assessmenthelper.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.utdallas.cs4485.assessmenthelper.entity.Observer;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverRequest;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRequestRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProfessorAccessTests {

    private final ObserverRequestRepository requests = mock(ObserverRequestRepository.class);
    private final ObserverRepository observers = mock(ObserverRepository.class);
    private final ProfessorAccess access = new ProfessorAccess(requests, observers);

    @BeforeEach
    void requestOneIsSentToProfessorSeven() {
        when(requests.findById(1))
                .thenReturn(Optional.of(new ObserverRequest(
                        1, 2, 3, LocalDateTime.of(2026, 10, 20, 12, 0), null, null, "PENDING", false)));
        when(observers.findById(3)).thenReturn(Optional.of(new Observer(3, 7, 1, true)));
    }

    @Test
    void requestedObserverMayAnswer() {
        assertThat(access.isRequestedObserver(professor(7), 1)).isTrue();
    }

    @Test
    void otherProfessorMayNotAnswer() {
        assertThat(access.isRequestedObserver(professor(8), 1)).isFalse();
    }

    @Test
    void committeeMemberWithoutAProfessorRecordMayNotAnswer() {
        AccountPrincipal committeeOnly = new AccountPrincipal(
                new CurrentUser(9, null, 1, "Committee Only", "committee@univ.edu", List.of(Role.AC)), null, true);

        assertThat(access.isRequestedObserver(committeeOnly, 1)).isFalse();
    }

    @Test
    void unknownRequestIsDenied() {
        assertThat(access.isRequestedObserver(professor(7), 99)).isFalse();
    }

    @Test
    void committeeMayViewFacultyAcrossSchools() {
        AccountPrincipal committee = new AccountPrincipal(
                new CurrentUser(9, 9, 2, "Committee", "committee@univ.edu", List.of(Role.AC)), null, true);
        assertThat(access.canView(committee, 7)).isTrue();
    }

    @Test
    void facultyMayViewOnlyTheirOwnRecords() {
        assertThat(access.canView(professor(7), 7)).isTrue();
        assertThat(access.canView(professor(7), 8)).isFalse();
    }

    private static AccountPrincipal professor(int professorId) {
        return new AccountPrincipal(
                new CurrentUser(
                        professorId, professorId, 1, "Test Professor", "test@univ.edu", List.of(Role.PROFESSOR)),
                null,
                true);
    }
}
