package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import edu.utdallas.cs4485.assessmenthelper.service.ObservationService.Answer;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationService.Status;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationService.ViewerRole;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationTemplateService.Criterion;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acceptance tests for US29 (observer uses the current in-app form), US30 (ratings and notes are recorded in the
 * form), and the observer's submission step of US31. Names start with the story they check.
 */
@SpringBootTest
@Import(AcceptanceSupport.Time.class)
@Transactional
class ObservationFormAcceptanceTests extends AcceptanceSupport {

    // ---------- US29: the form ----------

    @Test
    void us29_theFormShowsTheTemplateTheObservationWasCreatedWith() {
        int obs = observationId();
        var detail = observations.detail(obs, keira());
        int templateId =
                db.queryForObject("SELECT template_id FROM observation WHERE observation_id = ?", Integer.class, obs);

        assertThat(detail.template().templateId()).isEqualTo(templateId);
        assertThat(detail.template().templateName()).isEqualTo("Standard Classroom Observation");
        assertThat(detail.template().criteria()).isNotEmpty();
    }

    @Test
    void us29_questionsComeInTheOrderTheCommitteeSetAndEachHasAType() {
        var questions = criteria(observationId());

        assertThat(questions.stream().map(Criterion::displayOrder).toList()).isSorted();
        assertThat(questions).allSatisfy(q -> {
            assertThat(q.criterionText()).isNotBlank();
            assertThat(q.questionType()).isNotBlank();
        });
    }

    @Test
    void us29_ratingQuestionsHaveARangeAndTextQuestionsDoNot() {
        var questions = criteria(observationId());

        assertThat(questions.stream().filter(q -> "RATING".equals(q.questionType())))
                .isNotEmpty()
                .allSatisfy(q -> {
                    assertThat(q.ratingMin()).isNotNull();
                    assertThat(q.ratingMax()).isNotNull();
                    assertThat(q.ratingMin()).isLessThan(q.ratingMax());
                });
    }

    @Test
    void us29_beforeStartingTheObserverSeesTheFormButCanOnlyStartIt() {
        var detail = observations.detail(observationId(), keira());

        assertThat(detail.viewerRole()).isEqualTo(ViewerRole.OBSERVER);
        assertThat(detail.canStart()).isTrue();
        assertThat(detail.canEdit()).isFalse();
        assertThat(detail.locked()).isFalse();
        assertThat(detail.responses()).isEmpty();
    }

    @Test
    void us29_startingTheFormMovesItToInProgressAndKeepsTheAgreedDateAndTime() {
        var detail = keiraStarts(observationId());

        assertThat(detail.status()).isEqualTo(Status.IN_PROGRESS);
        assertThat(detail.observationDate()).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(detail.startTime()).isEqualTo(LocalTime.of(8, 30));
        assertThat(detail.canEdit()).isTrue();
        assertThat(detail.canStart()).isFalse();
    }

    @Test
    void us29_startingAnAlreadyStartedFormChangesNothing() {
        int obs = observationId();
        var first = keiraStarts(obs);
        var second = keiraStarts(obs);

        assertThat(second.status()).isEqualTo(Status.IN_PROGRESS);
        assertThat(second.observationDate()).isEqualTo(first.observationDate());
        assertThat(second.startTime()).isEqualTo(first.startTime());
    }

    @Test
    void us29_onlyTheObserverCanStartTheForm() {
        int obs = observationId();

        assertThatThrownBy(() -> observations.start(obs, leander())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> observations.start(obs, dario())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> observations.start(obs, kaia())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void us29_anUnknownObservationIsNotFound() {
        assertThatThrownBy(() -> observations.detail(999_999, keira())).isInstanceOf(NotFoundException.class);
    }

    // ---------- US30: ratings and notes ----------

    @Test
    void us30_answersCannotBeRecordedBeforeTheFormIsStarted() {
        int obs = observationId();

        assertThatThrownBy(() -> observations.saveResponses(obs, completeAnswers(obs), keira()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Start the observation");
        assertThat(storedResponseCount(obs)).isZero();
    }

    @Test
    void us30_aDraftWithOnlySomeAnswersIsSaved() {
        int obs = observationId();
        keiraStarts(obs);
        Criterion rating = firstRatingQuestion(obs);

        var detail = observations.saveResponses(
                obs, List.of(new Answer(rating.criterionId(), rating.ratingMax(), "Strong start.")), keira());

        assertThat(detail.status()).isEqualTo(Status.IN_PROGRESS);
        assertThat(detail.responses()).hasSize(1);
        assertThat(detail.responses().getFirst().rating()).isEqualTo(rating.ratingMax());
        assertThat(detail.responses().getFirst().comments()).isEqualTo("Strong start.");
    }

    @Test
    void us30_aRatingAndANoteAreStoredTogetherForOneQuestion() {
        int obs = observationId();
        keiraStarts(obs);
        Criterion rating = firstRatingQuestion(obs);

        observations.saveResponses(
                obs, List.of(new Answer(rating.criterionId(), rating.ratingMin(), "Needs work.")), keira());
        var reopened = observations.detail(obs, keira());

        assertThat(reopened.responses()).singleElement().satisfies(a -> {
            assertThat(a.criterionId()).isEqualTo(rating.criterionId());
            assertThat(a.rating()).isEqualTo(rating.ratingMin());
            assertThat(a.comments()).isEqualTo("Needs work.");
        });
    }

    @Test
    void us30_savingAgainReplacesThePreviousDraft() {
        int obs = observationId();
        keiraStarts(obs);
        Criterion rating = firstRatingQuestion(obs);

        observations.saveResponses(obs, completeAnswers(obs), keira());
        int full = storedResponseCount(obs);
        observations.saveResponses(obs, List.of(new Answer(rating.criterionId(), rating.ratingMax(), null)), keira());

        assertThat(full).isGreaterThan(1);
        assertThat(storedResponseCount(obs)).isEqualTo(1);
    }

    @Test
    void us30_aQuestionLeftBlankIsNotStored() {
        int obs = observationId();
        keiraStarts(obs);
        Criterion rating = firstRatingQuestion(obs);

        observations.saveResponses(obs, List.of(new Answer(rating.criterionId(), null, "   ")), keira());

        assertThat(storedResponseCount(obs)).isZero();
    }

    @Test
    void us30_commentsAreTrimmed() {
        int obs = observationId();
        keiraStarts(obs);
        Criterion rating = firstRatingQuestion(obs);

        var detail = observations.saveResponses(
                obs, List.of(new Answer(rating.criterionId(), rating.ratingMax(), "  Good pacing.  ")), keira());

        assertThat(detail.responses().getFirst().comments()).isEqualTo("Good pacing.");
    }

    @Test
    void us30_aRatingOutsideTheQuestionsRangeIsRejectedAndNothingIsSaved() {
        int obs = observationId();
        keiraStarts(obs);
        Criterion rating = firstRatingQuestion(obs);
        assumeTrue(rating.ratingMax() != null);

        assertThatThrownBy(() -> observations.saveResponses(
                        obs, List.of(new Answer(rating.criterionId(), rating.ratingMax() + 1, null)), keira()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rate");
        assertThat(storedResponseCount(obs)).isZero();
    }

    @Test
    void us30_aRatingBelowTheRangeIsRejected() {
        int obs = observationId();
        keiraStarts(obs);
        Criterion rating = firstRatingQuestion(obs);
        assumeTrue(rating.ratingMin() != null);

        assertThatThrownBy(() -> observations.saveResponses(
                        obs, List.of(new Answer(rating.criterionId(), rating.ratingMin() - 1, null)), keira()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void us30_oneBadAnswerStopsTheWholeSaveAndKeepsTheEarlierDraft() {
        int obs = observationId();
        keiraStarts(obs);
        Criterion rating = firstRatingQuestion(obs);
        assumeTrue(rating.ratingMax() != null);
        observations.saveResponses(
                obs, List.of(new Answer(rating.criterionId(), rating.ratingMin(), "First draft.")), keira());

        assertThatThrownBy(() -> observations.saveResponses(
                        obs,
                        List.of(
                                new Answer(rating.criterionId(), rating.ratingMax(), "Second draft."),
                                new Answer(999_999, 3, null)),
                        keira()))
                .isInstanceOf(IllegalArgumentException.class);

        var reopened = observations.detail(obs, keira());
        assertThat(reopened.responses())
                .singleElement()
                .satisfies(a -> assertThat(a.comments()).isEqualTo("First draft."));
    }

    @Test
    void us30_aQuestionThatIsNotOnTheFormIsRejected() {
        int obs = observationId();
        keiraStarts(obs);

        assertThatThrownBy(() -> observations.saveResponses(obs, List.of(new Answer(999_999, 3, null)), keira()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not on this form");
    }

    @Test
    void us30_theSameQuestionCannotBeAnsweredTwice() {
        int obs = observationId();
        keiraStarts(obs);
        Criterion rating = firstRatingQuestion(obs);

        assertThatThrownBy(() -> observations.saveResponses(
                        obs,
                        List.of(
                                new Answer(rating.criterionId(), rating.ratingMin(), null),
                                new Answer(rating.criterionId(), rating.ratingMax(), null)),
                        keira()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("more than once");
    }

    @Test
    void us30_aTextQuestionDoesNotTakeARating() {
        int obs = observationId();
        keiraStarts(obs);
        var text = criteria(obs).stream()
                .filter(q -> !"RATING".equals(q.questionType()))
                .findFirst();
        assumeTrue(text.isPresent());

        assertThatThrownBy(() -> observations.saveResponses(
                        obs, List.of(new Answer(text.get().criterionId(), 3, null)), keira()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("takes comments, not a rating");
    }

    @Test
    void us30_aCommentOverFourThousandCharactersIsRejected() {
        int obs = observationId();
        keiraStarts(obs);
        Criterion rating = firstRatingQuestion(obs);

        assertThatThrownBy(() -> observations.saveResponses(
                        obs, List.of(new Answer(rating.criterionId(), rating.ratingMin(), "x".repeat(4001))), keira()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 4000");
    }

    @Test
    void us30_aCommentOfExactlyFourThousandCharactersIsAccepted() {
        int obs = observationId();
        keiraStarts(obs);
        Criterion rating = firstRatingQuestion(obs);

        var detail = observations.saveResponses(
                obs, List.of(new Answer(rating.criterionId(), rating.ratingMin(), "x".repeat(4000))), keira());

        assertThat(detail.responses().getFirst().comments()).hasSize(4000);
    }

    @Test
    void us30_onlyTheObserverCanRecordAnswers() {
        int obs = observationId();
        keiraStarts(obs);

        assertThatThrownBy(() -> observations.saveResponses(obs, completeAnswers(obs), leander()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> observations.saveResponses(obs, completeAnswers(obs), dario()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(storedResponseCount(obs)).isZero();
    }

    // ---------- US31 (observer's part): submitting ----------

    @Test
    void us31_submittingWithoutAnsweringEveryRequiredQuestionIsRefused() {
        int obs = observationId();
        keiraStarts(obs);
        assumeTrue(criteria(obs).stream().anyMatch(Criterion::required));

        assertThatThrownBy(() -> observations.submit(obs, List.of(), keira()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Answer every required question");
        assertThat(observations.detail(obs, keira()).status()).isEqualTo(Status.IN_PROGRESS);
    }

    @Test
    void us31_theRefusalNamesTheQuestionsThatAreMissing() {
        int obs = observationId();
        keiraStarts(obs);
        var required = criteria(obs).stream().filter(Criterion::required).findFirst();
        assumeTrue(required.isPresent());

        assertThatThrownBy(() -> observations.submit(obs, List.of(), keira()))
                .hasMessageContaining(required.get().criterionText());
    }

    @Test
    void us31_submittingCompleteAnswersRecordsTheObserversSignOff() {
        int obs = observationId();

        var detail = keiraSubmits(obs);

        assertThat(detail.status()).isEqualTo(Status.SUBMITTED);
        assertThat(detail.submittedAt()).isNotNull();
        assertThat(detail.canEdit()).isFalse();
        assertThat(detail.locked()).isFalse();
        assertThat(storedResponseCount(obs)).isEqualTo(completeAnswers(obs).size());
    }

    @Test
    void us31_submittingTellsTheObserveeToReviewAndSignOff() {
        int obs = observationId();
        int before = notificationCount(LEANDER);

        keiraSubmits(obs);

        assertThat(notificationCount(LEANDER)).isEqualTo(before + 1);
        String latest = db.queryForObject(
                "SELECT message FROM observer_notification WHERE professor_id = ? ORDER BY notification_id DESC LIMIT 1",
                String.class,
                LEANDER);
        assertThat(latest).contains("submitted the observation").contains("sign off");
    }

    @Test
    void us31_afterSubmittingTheObserverCannotChangeTheAnswers() {
        int obs = observationId();
        keiraSubmits(obs);

        assertThatThrownBy(() -> observations.saveResponses(obs, completeAnswers(obs), keira()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already been submitted");
    }

    @Test
    void us31_theObserverCannotSubmitTwice() {
        int obs = observationId();
        keiraSubmits(obs);

        assertThatThrownBy(() -> observations.submit(obs, completeAnswers(obs), keira()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void us31_theObserverCannotRestartASubmittedForm() {
        int obs = observationId();
        keiraSubmits(obs);

        assertThatThrownBy(() -> observations.start(obs, keira())).isInstanceOf(ConflictException.class);
    }

    @Test
    void us31_submittedAnswersAreExactlyWhatTheObserverSent() {
        int obs = observationId();
        keiraSubmits(obs);

        var reopened = observations.detail(obs, keira());

        assertThat(reopened.responses()).containsExactlyInAnyOrderElementsOf(completeAnswers(obs));
    }
}
