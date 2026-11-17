package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationSignupRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserveeRepository;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationService.AuditEvent;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationService.Status;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationService.ViewerRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acceptance tests for US31 to US34 (sign-off, observee comments, locking) and US41 (only the people involved can read
 * a record), plus the audit log and link forwarding that the proposal's security section requires.
 */
@SpringBootTest
@Import(AcceptanceSupport.Time.class)
@Transactional
class SignOffAndLockingAcceptanceTests extends AcceptanceSupport {
    @Autowired
    private ObservationRepository observationRecords;

    @Autowired
    private EvaluationSignupRepository signups;

    @Autowired
    private ObserveeRepository observees;

    private List<AuditEvent> events(int obs) {
        return audit.auditLog(obs, kaia()).stream()
                .map(ObservationAuditService.AuditEntry::event)
                .toList();
    }

    private String observeeStatus(int obs) {
        int signupId = observationRecords.findById(obs).orElseThrow().signupId();
        int observeeId = signups.findById(signupId).orElseThrow().observeeId();
        return observees.findById(observeeId).orElseThrow().status();
    }

    // ---------- US41: who may read the record ----------

    @Test
    void us41_theObserveeCanReadTheRecordOnlyOnceTheObserverSubmits() {
        int obs = observationId();
        keiraStarts(obs);
        assertThatThrownBy(() -> observations.detail(obs, leander())).isInstanceOf(AccessDeniedException.class);

        observations.submit(obs, completeAnswers(obs), keira());
        var detail = observations.detail(obs, leander());

        assertThat(detail.viewerRole()).isEqualTo(ViewerRole.OBSERVEE);
        assertThat(detail.responses()).isNotEmpty();
        assertThat(detail.canSign()).isTrue();
        assertThat(detail.canEdit()).isFalse();
    }

    @Test
    void us41_theCommitteeCanReadOnlyACompletedRecordAcrossSchools() {
        int obs = observationId();
        keiraSubmits(obs);
        assertThatThrownBy(() -> observations.detail(obs, kaia())).isInstanceOf(AccessDeniedException.class);

        leanderSigns(obs, null);

        assertThat(observations.detail(obs, kaia()).viewerRole()).isEqualTo(ViewerRole.COMMITTEE);
        assertThat(observations.detail(obs, outsiderCommittee()).viewerRole()).isEqualTo(ViewerRole.COMMITTEE);
    }

    // ---------- US31 / US32: observee sign-off ----------

    @Test
    void us31_theObserveeCannotSignBeforeTheObserverHasSubmitted() {
        int obs = observationId();
        keiraStarts(obs);

        assertThatThrownBy(() -> leanderSigns(obs, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("has not submitted");
    }

    @Test
    void us31_onlyTheObserveeCanSignOff() {
        int obs = observationId();
        keiraSubmits(obs);

        assertThatThrownBy(() -> observations.observeeSign(obs, null, keira()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(observations.detail(obs, leander()).status()).isEqualTo(Status.SUBMITTED);
    }

    @Test
    void us31_signingOffCompletesTheEvaluationAndTellsTheObserver() {
        int obs = observationId();
        keiraSubmits(obs);
        int before = notificationCount(KEIRA);

        // US32: signing needs no comment, and does not mean agreeing.
        var detail = leanderSigns(obs, null);

        assertThat(detail.status()).isEqualTo(Status.COMPLETED);
        assertThat(detail.observeeSignedAt()).isAfterOrEqualTo(detail.submittedAt());
        assertThat(detail.observeeComments()).isNull();
        assertThat(observeeStatus(obs)).isEqualTo("COMPLETED");
        assertThat(notificationCount(KEIRA)).isEqualTo(before + 1);
    }

    // ---------- US33: observee comments ----------

    @Test
    void us33_theObserveesCommentsAreSavedWithoutChangingTheObserversAnswers() {
        int obs = observationId();
        keiraSubmits(obs);
        var answers = observations.detail(obs, leander()).responses();

        leanderSigns(obs, "I disagree with some of the ratings.");

        var detail = observations.detail(obs, kaia());
        assertThat(detail.observeeComments()).isEqualTo("I disagree with some of the ratings.");
        assertThat(detail.responses()).containsExactlyElementsOf(answers);
    }

    @Test
    void us33_aCommentOverTheLimitIsRejectedAndNothingChanges() {
        int obs = observationId();
        keiraSubmits(obs);

        assertThatThrownBy(() -> leanderSigns(obs, "x".repeat(4001)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 4000");

        var detail = observations.detail(obs, leander());
        assertThat(detail.status()).isEqualTo(Status.SUBMITTED);
        assertThat(detail.observeeComments()).isNull();
    }

    // ---------- US34: locking ----------

    @Test
    void us34_signingOffLocksTheRecordAndTheObserverCanStillReadIt() {
        int obs = observationId();
        assertThat(keiraSubmits(obs).locked()).isFalse();

        leanderSigns(obs, null);

        var detail = observations.detail(obs, keira());
        assertThat(detail.viewerRole()).isEqualTo(ViewerRole.OBSERVER);
        assertThat(detail.locked()).isTrue();
        assertThat(detail.canEdit()).isFalse();
        assertThat(detail.canStart()).isFalse();
    }

    @Test
    void us34_noOneCanChangeALockedRecord() {
        int obs = observationId();
        complete(obs, null);

        assertThatThrownBy(() -> observations.saveResponses(obs, completeAnswers(obs), keira()))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> observations.saveResponses(obs, completeAnswers(obs), kaia()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> leanderSigns(obs, "Changed my mind."))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already been signed off");
    }

    // ---------- audit log ----------

    @Test
    void audit_theLogRecordsEachStepInOrderWithWhoDidItAndWhen() {
        int obs = observationId();
        keiraStarts(obs);
        observations.saveResponses(obs, completeAnswers(obs), keira());
        observations.submit(obs, completeAnswers(obs), keira());
        leanderSigns(obs, "Thanks.");

        var entries = audit.auditLog(obs, kaia());

        assertThat(entries)
                .extracting(ObservationAuditService.AuditEntry::event)
                .containsExactly(
                        AuditEvent.STARTED,
                        AuditEvent.EDITED,
                        AuditEvent.SUBMITTED,
                        AuditEvent.OBSERVEE_COMMENTED,
                        AuditEvent.OBSERVEE_SIGNED,
                        AuditEvent.LOCKED);
        assertThat(entries).allSatisfy(e -> assertThat(e.occurredAt()).isNotNull());
        assertThat(entries.get(2).actorName()).isEqualTo("Keira Larkspur");
        assertThat(entries.get(4).actorName()).isEqualTo("Leander Coldwell");
    }

    @Test
    void audit_viewsOfALockedRecordAreLoggedButDraftViewsAreNot() {
        int obs = observationId();
        keiraStarts(obs);
        observations.detail(obs, keira());
        // The committee may read the log at any status, though not the record itself.
        assertThat(events(obs)).doesNotContain(AuditEvent.VIEWED);

        observations.submit(obs, completeAnswers(obs), keira());
        leanderSigns(obs, null);
        observations.detail(obs, keira());

        assertThat(events(obs)).containsOnlyOnce(AuditEvent.VIEWED);
    }

    // ---------- forwarding a record link ----------

    @Test
    void forward_theCommitteeForwardsALockedRecordWhichIsLoggedAndNotifiedButGivesNoAccess() {
        int obs = observationId();
        complete(obs, null);
        int before = notificationCount(DARIO);

        observations.forward(obs, DARIO, kaia());

        var forwarded = audit.auditLog(obs, kaia()).stream()
                .filter(e -> e.event() == AuditEvent.FORWARDED)
                .findFirst()
                .orElseThrow();
        assertThat(forwarded.actorName()).isEqualTo("Kaia Clearbrook");
        assertThat(forwarded.recipientName()).contains("Dario");
        assertThat(notificationCount(DARIO)).isEqualTo(before + 1);
        assertThatThrownBy(() -> observations.detail(obs, dario())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void forward_onlyALockedRecordCanBeForwarded() {
        int obs = observationId();
        keiraSubmits(obs);

        assertThatThrownBy(() -> observations.forward(obs, DARIO, kaia()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("locked");
    }
}
