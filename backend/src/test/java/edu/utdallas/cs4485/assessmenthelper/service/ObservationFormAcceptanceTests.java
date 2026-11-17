package edu.utdallas.cs4485.assessmenthelper.service;

import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.inactive;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.utdallas.cs4485.assessmenthelper.entity.ObservationCriterion;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationCriterionRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationTemplateRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverNotificationRepository;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationService.Answer;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationService.Status;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationTemplateService.Criterion;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
    void us29_theFormKeepsTheTemplateTheObservationWasCreatedWithInTheCommitteesOrder() {
        int obs = observationId();
        int templateId = observationRecords.findById(obs).orElseThrow().templateId();
        // The committee moves the first question to the end, then retires the template.
        int first = criteria(obs).getFirst().criterionId();
        var moved = criterionRecords.findById(first).orElseThrow();
        criterionRecords.save(new ObservationCriterion(
                moved.criterionId(),
                moved.templateId(),
                moved.criterionText(),
                moved.questionType(),
                moved.required(),
                99,
                moved.ratingMin(),
                moved.ratingMax(),
                moved.selected(),
                moved.notesEnabled()));
        templateRecords.findAll().forEach(t -> templateRecords.save(inactive(t)));

        var template = observations.detail(obs, keira()).template();

        assertThat(template.templateId()).isEqualTo(templateId);
        assertThat(template.criteria().getLast().criterionId()).isEqualTo(first);
    }

    @Test
    void us29_startingTheFormMovesItToInProgressAndKeepsTheAgreedDateAndTime() {
        int obs = observationId();
        assertThat(observations.detail(obs, keira()).canStart()).isTrue();

        var detail = keiraStarts(obs);

        assertThat(detail.status()).isEqualTo(Status.IN_PROGRESS);
        assertThat(detail.observationDate()).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(detail.startTime()).isEqualTo(LocalTime.of(8, 30));
        assertThat(detail.canEdit()).isTrue();
    }

    @Test
    void us29_startingBeforeThePlannedDayRecordsTodayAndTheCurrentMinute() {
        // A week before Leander's class, at 09:05:30 in Dallas: the observation is happening now, not on Nov 4.
        clock.set("2026-10-28T14:05:30Z");

        var detail = keiraStarts(observationId());

        assertThat(detail.observationDate()).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(detail.startTime()).isEqualTo(LocalTime.of(9, 5));
    }

    @Autowired
    private ObservationRepository observationRecords;

    @Autowired
    private ObservationCriterionRepository criterionRecords;

    @Autowired
    private ObservationTemplateRepository templateRecords;

    @Autowired
    private ObserverNotificationRepository notifications;

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
    void us30_aDraftWithARatingAndANoteForOnlySomeQuestionsIsSaved() {
        int obs = observationId();
        keiraStarts(obs);
        Criterion rating = firstRatingQuestion(obs);

        observations.saveResponses(
                obs, List.of(new Answer(rating.criterionId(), rating.ratingMax(), "  Strong start.  ")), keira());

        var reopened = observations.detail(obs, keira());
        assertThat(reopened.status()).isEqualTo(Status.IN_PROGRESS);
        assertThat(reopened.responses())
                .containsExactly(new Answer(rating.criterionId(), rating.ratingMax(), "Strong start."));
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
    void us30_aRatingOutsideTheQuestionsRangeIsRejectedAndTheEarlierDraftIsKept() {
        int obs = observationId();
        keiraStarts(obs);
        Criterion rating = firstRatingQuestion(obs);
        observations.saveResponses(
                obs, List.of(new Answer(rating.criterionId(), rating.ratingMin(), "First draft.")), keira());

        assertThatThrownBy(() -> observations.saveResponses(
                        obs,
                        List.of(new Answer(rating.criterionId(), rating.ratingMax() + 1, "Second draft.")),
                        keira()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rate");

        assertThat(observations.detail(obs, keira()).responses())
                .singleElement()
                .satisfies(a -> assertThat(a.comments()).isEqualTo("First draft."));
    }

    // ---------- US31 (observer's part): submitting ----------

    @Test
    void us31_submittingWithoutAnsweringEveryRequiredQuestionIsRefused() {
        int obs = observationId();
        keiraStarts(obs);

        assertThatThrownBy(() -> observations.submit(obs, List.of(), keira()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Answer every required question");
        assertThat(observations.detail(obs, keira()).status()).isEqualTo(Status.IN_PROGRESS);
    }

    @Test
    void us31_submittingCompleteAnswersRecordsTheObserversSignOffAndTellsTheObservee() {
        int obs = observationId();
        int before = notificationCount(LEANDER);

        var detail = keiraSubmits(obs);

        assertThat(detail.status()).isEqualTo(Status.SUBMITTED);
        assertThat(detail.submittedAt()).isNotNull();
        assertThat(detail.responses()).containsExactlyInAnyOrderElementsOf(completeAnswers(obs));
        assertThat(notificationCount(LEANDER)).isEqualTo(before + 1);
    }

    @Test
    void us31_afterSubmittingTheObserverCannotChangeOrResubmitTheAnswers() {
        int obs = observationId();
        keiraSubmits(obs);

        assertThatThrownBy(() -> observations.saveResponses(obs, completeAnswers(obs), keira()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already been submitted");
        assertThatThrownBy(() -> observations.submit(obs, completeAnswers(obs), keira()))
                .isInstanceOf(ConflictException.class);
    }
}
