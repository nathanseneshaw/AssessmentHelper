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
    void us41_theObserveeCannotOpenTheRecordWhileItIsStillADraft() {
        int obs = observationId();

        assertThatThrownBy(() -> observations.detail(obs, leander())).isInstanceOf(AccessDeniedException.class);
        keiraStarts(obs);
        assertThatThrownBy(() -> observations.detail(obs, leander())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void us41_theObserveeCanReadTheRecordOnceTheObserverSubmits() {
        int obs = observationId();
        keiraSubmits(obs);

        var detail = observations.detail(obs, leander());

        assertThat(detail.viewerRole()).isEqualTo(ViewerRole.OBSERVEE);
        assertThat(detail.responses()).isNotEmpty();
        assertThat(detail.canSign()).isTrue();
        assertThat(detail.canEdit()).isFalse();
    }

    @Test
    void us41_aProfessorNotInvolvedCanNeverReadTheRecord() {
        int obs = observationId();
        assertThatThrownBy(() -> observations.detail(obs, dario())).isInstanceOf(AccessDeniedException.class);

        keiraSubmits(obs);
        assertThatThrownBy(() -> observations.detail(obs, dario())).isInstanceOf(AccessDeniedException.class);

        leanderSigns(obs, null);
        assertThatThrownBy(() -> observations.detail(obs, dario())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void us41_theCommitteeCannotReadADraftOrASubmittedRecordOnlyACompletedOne() {
        int obs = observationId();
        keiraStarts(obs);
        assertThatThrownBy(() -> observations.detail(obs, kaia())).isInstanceOf(AccessDeniedException.class);

        observations.submit(obs, completeAnswers(obs), keira());
        assertThatThrownBy(() -> observations.detail(obs, kaia())).isInstanceOf(AccessDeniedException.class);

        leanderSigns(obs, null);
        var detail = observations.detail(obs, kaia());
        assertThat(detail.viewerRole()).isEqualTo(ViewerRole.COMMITTEE);
        assertThat(detail.responses()).isNotEmpty();
    }

    @Test
    void us41_aCommitteeMemberFromAnotherSchoolCanReadTheRecord() {
        int obs = observationId();
        complete(obs, null);

        assertThat(observations.detail(obs, outsiderCommittee()).viewerRole()).isEqualTo(ViewerRole.COMMITTEE);
    }

    @Test
    void us41_theObserverCanStillReadTheRecordAfterItIsLocked() {
        int obs = observationId();
        complete(obs, null);

        var detail = observations.detail(obs, keira());

        assertThat(detail.viewerRole()).isEqualTo(ViewerRole.OBSERVER);
        assertThat(detail.locked()).isTrue();
    }

    @Test
    void us41_aRefusedReadIsNotWrittenToTheAuditLog() {
        int obs = observationId();
        complete(obs, null);
        int before = events(obs).size();

        assertThatThrownBy(() -> observations.detail(obs, dario())).isInstanceOf(AccessDeniedException.class);

        assertThat(events(obs)).hasSize(before);
    }

    // ---------- US31: observee sign-off ----------

    @Test
    void us31_theObserveeCannotSignBeforeTheObserverHasSubmitted() {
        int obs = observationId();

        assertThatThrownBy(() -> leanderSigns(obs, "Too early."))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("has not submitted");
        keiraStarts(obs);
        assertThatThrownBy(() -> leanderSigns(obs, "Still too early.")).isInstanceOf(ConflictException.class);
    }

    @Test
    void us31_onlyTheObserveeCanSignOff() {
        int obs = observationId();
        keiraSubmits(obs);

        assertThatThrownBy(() -> observations.observeeSign(obs, null, keira()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> observations.observeeSign(obs, null, dario()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> observations.observeeSign(obs, null, kaia()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(observations.detail(obs, leander()).status()).isEqualTo(Status.SUBMITTED);
    }

    @Test
    void us31_bothSignaturesCompleteTheRecord() {
        int obs = observationId();

        var detail = complete(obs, "Thanks, Keira.");

        assertThat(detail.status()).isEqualTo(Status.COMPLETED);
        assertThat(detail.submittedAt()).isNotNull();
        assertThat(detail.observeeSignedAt()).isNotNull();
        assertThat(detail.observeeSignedAt()).isAfterOrEqualTo(detail.submittedAt());
    }

    @Test
    void us31_aRecordWithOnlyTheObserversSignatureIsNotCompleted() {
        int obs = observationId();

        var detail = keiraSubmits(obs);

        assertThat(detail.status()).isNotEqualTo(Status.COMPLETED);
        assertThat(detail.observeeSignedAt()).isNull();
        assertThat(observeeStatus(obs)).isNotEqualTo("COMPLETED");
    }

    @Test
    void us31_signingOffMarksTheObserveesEvaluationCompleted() {
        int obs = observationId();
        complete(obs, null);

        assertThat(observeeStatus(obs)).isEqualTo("COMPLETED");
        assertThat(observationRecords.findById(obs).orElseThrow().completedAt()).isNotNull();
    }

    @Test
    void us31_theObserverIsToldTheRecordIsComplete() {
        int obs = observationId();
        keiraSubmits(obs);
        int before = notificationCount(KEIRA);

        leanderSigns(obs, null);

        assertThat(notificationCount(KEIRA)).isEqualTo(before + 1);
    }

    @Test
    void us31_theRecordCannotBeSignedOffTwice() {
        int obs = observationId();
        complete(obs, null);

        assertThatThrownBy(() -> leanderSigns(obs, "Again."))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already been signed off");
    }

    // ---------- US32: signing does not mean agreeing ----------

    @Test
    void us32_theObserveeCanSignWithoutAnyCommentOrAgreement() {
        int obs = observationId();
        keiraSubmits(obs);

        var detail = leanderSigns(obs, null);

        assertThat(detail.status()).isEqualTo(Status.COMPLETED);
        assertThat(detail.observeeComments()).isNull();
    }

    @Test
    void us32_aBlankCommentIsTreatedAsNoComment() {
        int obs = observationId();
        keiraSubmits(obs);

        var detail = leanderSigns(obs, "    ");

        assertThat(detail.observeeComments()).isNull();
        assertThat(events(obs)).doesNotContain(AuditEvent.OBSERVEE_COMMENTED);
    }

    @Test
    void us32_signingDoesNotChangeAnyOfTheObserversRatingsOrNotes() {
        int obs = observationId();
        keiraSubmits(obs);
        var before = observations.detail(obs, leander()).responses();

        leanderSigns(obs, "I disagree with some of the ratings.");

        assertThat(observations.detail(obs, leander()).responses()).containsExactlyElementsOf(before);
    }

    // ---------- US33: observee comments ----------

    @Test
    void us33_theObserveesCommentsAreSavedWithTheFinalRecord() {
        int obs = observationId();
        keiraSubmits(obs);

        leanderSigns(obs, "  Thanks, Keira. I will try more worked examples.  ");

        assertThat(observations.detail(obs, keira()).observeeComments())
                .isEqualTo("Thanks, Keira. I will try more worked examples.");
        assertThat(observations.detail(obs, kaia()).observeeComments())
                .isEqualTo("Thanks, Keira. I will try more worked examples.");
    }

    @Test
    void us33_theObserveesCommentsStaySeparateFromTheObserversNotes() {
        int obs = observationId();
        keiraSubmits(obs);
        var notesBefore = observations.detail(obs, leander()).responses();

        leanderSigns(obs, "My side of the observation.");

        var detail = observations.detail(obs, leander());
        assertThat(detail.responses()).containsExactlyElementsOf(notesBefore);
        assertThat(detail.responses()).noneMatch(a -> "My side of the observation.".equals(a.comments()));
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
        assertThat(observeeStatus(obs)).isNotEqualTo("COMPLETED");
    }

    @Test
    void us33_aCommentOfExactlyTheLimitIsAccepted() {
        int obs = observationId();
        keiraSubmits(obs);

        var detail = leanderSigns(obs, "x".repeat(4000));

        assertThat(detail.observeeComments()).hasSize(4000);
    }

    // ---------- US34: locking ----------

    @Test
    void us34_signingOffLocksTheRecord() {
        int obs = observationId();

        var detail = complete(obs, null);

        assertThat(detail.locked()).isTrue();
        assertThat(detail.canEdit()).isFalse();
        assertThat(detail.canSign()).isFalse();
        assertThat(detail.canStart()).isFalse();
    }

    @Test
    void us34_aRecordIsNotLockedUntilBothHaveSigned() {
        int obs = observationId();

        assertThat(observations.detail(obs, keira()).locked()).isFalse();
        keiraStarts(obs);
        assertThat(observations.detail(obs, keira()).locked()).isFalse();
        observations.submit(obs, completeAnswers(obs), keira());
        assertThat(observations.detail(obs, keira()).locked()).isFalse();
        leanderSigns(obs, null);
        assertThat(observations.detail(obs, keira()).locked()).isTrue();
    }

    @Test
    void us34_theObserverCannotEditALockedRecord() {
        int obs = observationId();
        complete(obs, null);

        assertThatThrownBy(() -> observations.saveResponses(obs, completeAnswers(obs), keira()))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> observations.submit(obs, completeAnswers(obs), keira()))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> observations.start(obs, keira())).isInstanceOf(ConflictException.class);
    }

    @Test
    void us34_noOneIncludingTheCommitteeCanEditALockedRecord() {
        int obs = observationId();
        complete(obs, null);

        assertThatThrownBy(() -> observations.saveResponses(obs, completeAnswers(obs), kaia()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> observations.saveResponses(obs, completeAnswers(obs), leander()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> observations.observeeSign(obs, "Changed my mind.", leander()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void us34_aFailedEditAttemptLeavesTheRecordExactlyAsItWas() {
        int obs = observationId();
        complete(obs, "Original comment.");
        var before = observations.detail(obs, keira());

        assertThatThrownBy(() -> observations.saveResponses(obs, List.of(), keira()))
                .isInstanceOf(ConflictException.class);

        var after = observations.detail(obs, keira());
        assertThat(after.responses()).containsExactlyElementsOf(before.responses());
        assertThat(after.observeeComments()).isEqualTo("Original comment.");
        assertThat(after.status()).isEqualTo(Status.COMPLETED);
    }

    // ---------- audit log ----------

    @Test
    void audit_theLogRecordsTheWorkflowInTheOrderItHappened() {
        int obs = observationId();
        complete(obs, "Thanks.");

        assertThat(events(obs))
                .containsSubsequence(
                        AuditEvent.STARTED,
                        AuditEvent.SUBMITTED,
                        AuditEvent.OBSERVEE_COMMENTED,
                        AuditEvent.OBSERVEE_SIGNED,
                        AuditEvent.LOCKED);
    }

    @Test
    void audit_eachEntryNamesWhoDidItAndWhen() {
        int obs = observationId();
        complete(obs, null);

        var entries = audit.auditLog(obs, kaia());

        assertThat(entries).isNotEmpty().allSatisfy(e -> {
            assertThat(e.actorName()).isNotBlank();
            assertThat(e.occurredAt()).isNotNull();
        });
        assertThat(entries.stream()
                        .filter(e -> e.event() == AuditEvent.SUBMITTED)
                        .findFirst()
                        .orElseThrow()
                        .actorName())
                .isEqualTo("Keira Larkspur");
        assertThat(entries.stream()
                        .filter(e -> e.event() == AuditEvent.OBSERVEE_SIGNED)
                        .findFirst()
                        .orElseThrow()
                        .actorName())
                .isEqualTo("Leander Coldwell");
    }

    @Test
    void audit_everyViewOfALockedRecordIsLoggedButDraftViewsAreNot() {
        int obs = observationId();
        keiraStarts(obs);
        observations.detail(obs, keira());
        assertThat(events(obs)).doesNotContain(AuditEvent.VIEWED);

        observations.submit(obs, completeAnswers(obs), keira());
        leanderSigns(obs, null);
        observations.detail(obs, keira());
        observations.detail(obs, leander());

        assertThat(events(obs).stream().filter(e -> e == AuditEvent.VIEWED).count())
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void audit_theLogOnlyGrowsAndNeverLosesEarlierEntries() {
        int obs = observationId();
        keiraStarts(obs);
        var afterStart = events(obs);

        observations.submit(obs, completeAnswers(obs), keira());
        var afterSubmit = events(obs);

        assertThat(afterSubmit).startsWith(afterStart.toArray(new AuditEvent[0]));
        assertThat(afterSubmit.size()).isGreaterThan(afterStart.size());
    }

    @Test
    void audit_onlyCommitteeMembersAcrossSchoolsCanReadTheLog() {
        int obs = observationId();
        complete(obs, null);

        assertThatThrownBy(() -> audit.auditLog(obs, keira())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> audit.auditLog(obs, leander())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> audit.auditLog(obs, dario())).isInstanceOf(AccessDeniedException.class);
        assertThat(audit.auditLog(obs, outsiderCommittee())).isNotEmpty();
        assertThat(audit.auditLog(obs, kaia())).isNotEmpty();
    }

    @Test
    void audit_theCommitteeCanReadTheLogEvenBeforeTheRecordIsComplete() {
        int obs = observationId();
        keiraStarts(obs);

        assertThat(events(obs)).contains(AuditEvent.STARTED);
    }

    @Test
    void audit_aMissingObservationHasNoLog() {
        assertThatThrownBy(() -> audit.auditLog(999_999, kaia())).isInstanceOf(NotFoundException.class);
    }

    // ---------- forwarding a record link ----------

    @Test
    void forward_theCommitteeCanForwardALockedRecordAndTheForwardIsLogged() {
        int obs = observationId();
        complete(obs, null);

        observations.forward(obs, DARIO, kaia());

        var forwarded = audit.auditLog(obs, kaia()).stream()
                .filter(e -> e.event() == AuditEvent.FORWARDED)
                .findFirst()
                .orElseThrow();
        assertThat(forwarded.actorName()).isEqualTo("Kaia Clearbrook");
        assertThat(forwarded.recipientName()).contains("Dario");
    }

    @Test
    void forward_doesNotGiveTheRecipientAccess() {
        int obs = observationId();
        complete(obs, null);

        observations.forward(obs, DARIO, kaia());

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

    @Test
    void forward_onlyCommitteeMembersCanForward() {
        int obs = observationId();
        complete(obs, null);

        assertThatThrownBy(() -> observations.forward(obs, DARIO, keira())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> observations.forward(obs, DARIO, leander())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void forward_aCommitteeMemberFromAnotherSchoolCanForward() {
        int obs = observationId();
        complete(obs, null);

        int before = notificationCount(DARIO);
        observations.forward(obs, DARIO, outsiderCommittee());
        assertThat(notificationCount(DARIO)).isEqualTo(before + 1);
    }

    @Test
    void forward_toAProfessorWhoDoesNotExistIsRejected() {
        int obs = observationId();
        complete(obs, null);

        assertThatThrownBy(() -> observations.forward(obs, 999_999, kaia())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void forward_theRecipientIsNotified() {
        int obs = observationId();
        complete(obs, null);
        int before = notificationCount(DARIO);

        observations.forward(obs, DARIO, kaia());

        assertThat(notificationCount(DARIO)).isEqualTo(before + 1);
    }
}
