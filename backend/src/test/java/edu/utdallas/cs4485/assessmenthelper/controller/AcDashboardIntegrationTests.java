package edu.utdallas.cs4485.assessmenthelper.controller;

import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.newFeedback;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.observerOf;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.only;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.signupOf;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.withFeedbackDeadline;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.withReleasedAt;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.withStatus;
import static java.util.Comparator.comparing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationCycle;
import edu.utdallas.cs4485.assessmenthelper.entity.Observation;
import edu.utdallas.cs4485.assessmenthelper.entity.Observee;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverListState;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverMatch;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverNotification;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserveeRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverNotificationRepository;
import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import edu.utdallas.cs4485.assessmenthelper.service.ObserverEligibilityService;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs on data.sql's Fall 2026 demo (evaluation cycle 1). 13 faculty are due. Leander (13), Arlo (26), Avery (31),
 * Keira (41), Xiomara (54) and Perrin (17) have signed up and all have generated lists; Leander is overdue rather than
 * due, so five due faculty have signed up. Only Leander's signup has an observation, SCHEDULED for 2026-11-04. The
 * lists for Leander and Avery have four candidates each; the other lists are initially empty, including Rowan's.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AcDashboardIntegrationTests.Time.class)
@Transactional
class AcDashboardIntegrationTests {
    private static final String TODAY = "2026-10-26T15:00:00Z";

    /** A fixed clock that a test can move, so one context can check dates on both sides of an observation. */
    static class MovableClock extends Clock {
        private Instant instant = Instant.parse(TODAY);

        void set(String instant) {
            this.instant = Instant.parse(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("America/Chicago");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration
    static class Time {
        @Bean
        @Primary
        MovableClock dashboardClock() {
            return new MovableClock();
        }
    }

    private static final String METRICS = "/api/ac-dashboard/metrics";
    private static final String OBSERVATIONS = "/api/ac-dashboard/observations";
    private static final String REMINDERS = "/api/ac-dashboard/observations/reminders";
    private static final int HOLLIS = 9, LEANDER = 13, ARLO = 26, AVERY = 31, KEIRA = 41;

    private static final AccountPrincipal KAIA = new AccountPrincipal(
            new CurrentUser(
                    1, 12, 1, "Kaia Clearbrook", "kaia.clearbrook@example.edu", List.of(Role.AC, Role.PROFESSOR)),
            null,
            true);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcAggregateTemplate db;

    @Autowired
    private ObserveeRepository observees;

    @Autowired
    private ObservationRepository observations;

    @Autowired
    private ObserverNotificationRepository notifications;

    @Autowired
    private MovableClock clock;

    @Autowired
    private ObserverEligibilityService matching;

    @BeforeEach
    void resetClock() {
        clock.set(TODAY);
    }

    @Test
    void metricsMatchSeedDataWhenCommitteeLoadsDashboard() throws Exception {
        metrics().andExpect(status().isOk()).andExpect(content().json("""
                        {"cycleId":1,"cycleLabel":"Fall 2026","facultyDue":13,"listSize":5,
                        "participation":{"count":5,"total":13},"observerUtilization":{"count":1,"total":8},
                        "listSufficiency":{"count":0,"total":8},"missingObservations":0,
                        "surveyCompletion":{"count":0,"total":8},"surveyStatus":"NOT_OPEN"}
                        """, JsonCompareMode.STRICT));
    }

    @Test
    void surveyCompletionCountsFeedbackFromTheObserveesItIsOfferedTo() throws Exception {
        // The survey opens the day after the 2026-11-15 observation deadline and closes after 2026-12-01.
        clock.set("2026-11-15T15:00:00Z");
        metrics().andExpect(jsonPath("$.surveyStatus").value("NOT_OPEN"));
        clock.set("2026-11-16T15:00:00Z");
        metrics()
                .andExpect(jsonPath("$.surveyStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.surveyCompletion.count").value(0))
                .andExpect(jsonPath("$.surveyCompletion.total").value(8));

        submitFeedback(LEANDER);
        submitFeedback(ARLO);
        metrics()
                .andExpect(jsonPath("$.surveyCompletion.count").value(2))
                .andExpect(jsonPath("$.surveyCompletion.total").value(8));

        // A deferred observee is not offered the survey, so neither their answer nor their place counts.
        Observee arlo = only(observees.findByProfessorId(ARLO), o -> o.cycleId() == 1);
        db.update(new Observee(arlo.observeeId(), arlo.professorId(), arlo.cycleId(), "DEFERRED"));
        // Feedback from someone who is not an observee this cycle is not counted either.
        submitFeedback(HOLLIS);
        metrics()
                .andExpect(jsonPath("$.surveyCompletion.count").value(1))
                .andExpect(jsonPath("$.surveyCompletion.total").value(7));

        clock.set("2026-12-02T15:00:00Z");
        metrics()
                .andExpect(jsonPath("$.surveyStatus").value("CLOSED"))
                .andExpect(jsonPath("$.surveyCompletion.count").value(1));

        updateCycle(c -> withFeedbackDeadline(c, null));
        metrics().andExpect(jsonPath("$.surveyStatus").value("UNCONFIGURED"));
    }

    private void submitFeedback(int professorId) {
        db.insert(newFeedback(1, professorId, null, 4, LocalDate.parse("2026-11-16")));
    }

    @Test
    void observationIsMissingWhenCycleDeadlinePassesUntilItIsCompleted() throws Exception {
        String leander = "$.observations[?(@.observeeId == " + LEANDER + ")]";
        // Leander's observation date (2026-11-04) passing is not enough; the 2026-11-15 cycle deadline is.
        clock.set("2026-11-05T15:00:00Z");
        expectMissing(0);
        setObservationStatus(LEANDER, "SUBMITTED");
        observations().andExpect(jsonPath(leander + ".status").value("PENDING_SIGN_OFF"));

        // After the deadline every signup without a completed observation is missing.
        clock.set("2026-11-16T15:00:00Z");
        expectMissing(8);
        observations()
                .andExpect(jsonPath(leander + ".status").value("MISSING"))
                .andExpect(jsonPath(leander + ".missingReason").value("Not signed"));

        setObservationStatus(LEANDER, "COMPLETED");
        expectMissing(7);
        observations().andExpect(jsonPath(leander + ".status").value("COMPLETED"));
    }

    @Test
    void listSufficiencyCountsAListWhenItIsFilledToListSize() throws Exception {
        // Leander's list is one candidate short of the list size until Arlo is added.
        db.insert(new ObserverMatch(
                null, signupOf(db, LEANDER), observerOf(db, ARLO), LocalDate.parse("2026-10-26"), "PROPOSED"));
        metrics()
                .andExpect(jsonPath("$.listSize").value(ObserverEligibilityService.LIST_SIZE))
                .andExpect(jsonPath("$.listSufficiency.count").value(1))
                .andExpect(jsonPath("$.listSufficiency.total").value(8));
    }

    @Test
    void observationsListHasEverySignupWhenListsAreGenerated() throws Exception {
        String leander = "$.observations[?(@.observeeId == " + LEANDER + ")]";
        observations()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycleId").value(1))
                .andExpect(jsonPath("$.observations.length()").value(8))
                .andExpect(jsonPath(leander + ".observer").value("Keira Larkspur"))
                .andExpect(jsonPath(leander + ".date").value("2026-11-04"))
                .andExpect(jsonPath(leander + ".status").value("SCHEDULED"))
                .andExpect(jsonPath("$.observations[?(@.status == 'AWAITING_OBSERVER')]", hasSize(7)));
    }

    @Test
    void remindersAreSentWhenObservationsAreOutstandingOrListsAreReleased() throws Exception {
        // Keira is reminded to complete Leander's scheduled observation. Avery's list is released but she has not
        // asked anyone. The other lists are not released, so their observees cannot request anyone yet.
        mvc.perform(as(KAIA, post(REMINDERS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(2));

        assertThat(remindersFor(LEANDER)).isEmpty();
        assertThat(remindersFor(KEIRA)).hasSize(1);
        assertThat(remindersFor(AVERY)).singleElement().asString().contains("choose an observer");
        assertThat(remindersFor(ARLO)).isEmpty();
    }

    @Test
    void submittedFormsRemindOnlyTheObserveeBeforeAndAfterTheDeadline() throws Exception {
        setObservationStatus(LEANDER, "SUBMITTED");
        mvc.perform(as(KAIA, post(REMINDERS))).andExpect(jsonPath("$.sent").value(2));
        assertThat(remindersFor(KEIRA)).isEmpty();
        assertThat(remindersFor(LEANDER)).singleElement().asString().contains("review and sign");
        clock.set("2026-11-16T15:00:00Z");
        mvc.perform(as(KAIA, post(REMINDERS))).andExpect(jsonPath("$.sent").value(2));
        assertThat(remindersFor(LEANDER)).hasSize(2);
        assertThat(remindersFor(KEIRA)).isEmpty();
        setObservationStatus(LEANDER, "COMPLETED");
        mvc.perform(as(KAIA, post(REMINDERS))).andExpect(jsonPath("$.sent").value(1));
        assertThat(remindersFor(LEANDER)).hasSize(2);
    }

    @Test
    void remindersSkipCaseWhenItIsDeferred() throws Exception {
        clock.set("2026-11-02T15:00:00Z");
        int signup = signupOf(db, 27);
        matching.defer(signup, 12, "No graduate-level observers available");
        observations()
                .andExpect(
                        jsonPath("$.observations[?(@.observeeId == 27)].status").value("DEFERRED"))
                .andExpect(jsonPath("$.observations[?(@.observeeId == 27)].deferral.targetSemester")
                        .value("Spring 2027"));
        metrics()
                .andExpect(jsonPath("$.observerUtilization.total").value(7))
                .andExpect(jsonPath("$.listSufficiency.total").value(7));
        db.update(withReleasedAt(
                db.findById(signup, ObserverListState.class), LocalDateTime.parse("2026-11-02T09:00:00")));
        int before = notifications.findByProfessorId(27).size();
        mvc.perform(as(KAIA, post(REMINDERS))).andExpect(status().isOk());
        assertThat(notifications.findByProfessorId(27)).hasSize(before);
    }

    private ResultActions metrics() throws Exception {
        return mvc.perform(get(METRICS).with(user(KAIA)));
    }

    private ResultActions observations() throws Exception {
        return mvc.perform(get(OBSERVATIONS).with(user(KAIA)));
    }

    // Signs the request in and adds a CSRF token the way the frontend gets one. The csrf() helper is not used
    // because it changes the shared Spring context (see AuthenticationTests).
    private MockHttpServletRequestBuilder as(AccountPrincipal account, MockHttpServletRequestBuilder request)
            throws Exception {
        Cookie token =
                mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request.with(user(account)).cookie(token).header("X-XSRF-TOKEN", token.getValue());
    }

    private List<String> remindersFor(int professorId) {
        return notifications.findByProfessorId(professorId).stream()
                .sorted(comparing(ObserverNotification::notificationId))
                .map(ObserverNotification::message)
                .filter(message -> message.startsWith("Reminder"))
                .toList();
    }

    private ResultActions expectMissing(int count) throws Exception {
        return metrics()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missingObservations").value(count));
    }

    private void updateCycle(UnaryOperator<EvaluationCycle> change) {
        db.update(change.apply(db.findById(1, EvaluationCycle.class)));
    }

    private void setObservationStatus(int professorId, String status) {
        updateObservations(professorId, o -> withStatus(o, status));
    }

    private void updateObservations(int professorId, UnaryOperator<Observation> change) {
        observations.findBySignupIdIn(List.of(signupOf(db, professorId))).forEach(o -> db.update(change.apply(o)));
    }
}
