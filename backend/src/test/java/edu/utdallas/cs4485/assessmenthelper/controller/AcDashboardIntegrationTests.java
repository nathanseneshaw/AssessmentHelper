package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import edu.utdallas.cs4485.assessmenthelper.service.ObserverEligibilityService;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private static final AccountPrincipal DARIO = new AccountPrincipal(
            new CurrentUser(2, 34, 1, "Dario Hollowell", "dario.hollowell@example.edu", List.of(Role.PROFESSOR)),
            null,
            true);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private MovableClock clock;

    @Autowired
    private edu.utdallas.cs4485.assessmenthelper.service.ObserverEligibilityService matching;

    @BeforeEach
    void resetClock() {
        clock.set(TODAY);
    }

    @Test
    void committeeSeesTheSeededCycleMetrics() throws Exception {
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
        db.update("UPDATE observee SET status = 'DEFERRED' WHERE professor_id = ? AND cycle_id = 1", ARLO);
        // Feedback from someone who is not an observee this cycle is not counted either.
        submitFeedback(HOLLIS);
        metrics()
                .andExpect(jsonPath("$.surveyCompletion.count").value(1))
                .andExpect(jsonPath("$.surveyCompletion.total").value(7));

        clock.set("2026-12-02T15:00:00Z");
        metrics()
                .andExpect(jsonPath("$.surveyStatus").value("CLOSED"))
                .andExpect(jsonPath("$.surveyCompletion.count").value(1));

        db.update("UPDATE evaluation_cycle SET feedback_deadline = NULL WHERE cycle_id = 1");
        metrics().andExpect(jsonPath("$.surveyStatus").value("UNCONFIGURED"));
    }

    private void submitFeedback(int professorId) {
        db.update("""
                INSERT INTO process_feedback (cycle_id, professor_id, overall_rating, submitted_date)
                VALUES (1, ?, 4, DATE '2026-11-16')
                """, professorId);
    }

    @Test
    void onlyTheCommitteeCanSeeTheMetrics() throws Exception {
        mvc.perform(get(METRICS).with(user(DARIO))).andExpect(status().isForbidden());
        mvc.perform(get(METRICS)).andExpect(status().isUnauthorized());
    }

    @Test
    void participationCountsDueFacultyWhoseSignupIsNotCancelled() throws Exception {
        // Hollis is due. His new signup has no generated list yet, so it is not one of the cycle's matched signups.
        db.update("INSERT INTO observee (professor_id, cycle_id, status) VALUES (?, 1, 'ACTIVE')", HOLLIS);
        db.update("""
                INSERT INTO evaluation_signup (observee_id, section_id, signup_date, status)
                VALUES ((SELECT observee_id FROM observee WHERE professor_id = ? AND cycle_id = 1), 284,
                    DATE '2026-10-26', 'PENDING')
                """, HOLLIS);
        metrics()
                .andExpect(jsonPath("$.participation.count").value(6))
                .andExpect(jsonPath("$.participation.total").value(13))
                .andExpect(jsonPath("$.observerUtilization.total").value(8));

        db.update("UPDATE evaluation_signup SET status = 'CANCELLED' WHERE signup_id = ?", signupOf(ARLO));
        metrics()
                .andExpect(jsonPath("$.participation.count").value(5))
                .andExpect(jsonPath("$.observerUtilization.total").value(7))
                .andExpect(jsonPath("$.listSufficiency.total").value(7));
    }

    @Test
    void missingObservationsArePastDatedAndNotCompleted() throws Exception {
        // Leander's observation on 2026-11-04 is missing only once that day has passed.
        expectMissing(0);
        clock.set("2026-11-04T15:00:00Z");
        expectMissing(0);
        clock.set("2026-11-05T15:00:00Z");
        expectMissing(1);

        // Accepting a request records an ACCEPTED observation, which counts once its date has passed.
        db.update("""
                INSERT INTO observation (signup_id, observer_id, template_id, observation_date, status)
                VALUES (?, ?, (SELECT MIN(template_id) FROM observation_template), DATE '2026-11-02', 'ACCEPTED')
                """, signupOf(AVERY), observerOf(KEIRA));
        expectMissing(2).andExpect(jsonPath("$.observerUtilization.count").value(2));
        db.update("UPDATE observation SET observation_date = NULL WHERE signup_id = ?", signupOf(AVERY));
        expectMissing(1);

        db.update("UPDATE observation SET status = 'Completed' WHERE signup_id = ?", signupOf(LEANDER));
        expectMissing(0).andExpect(jsonPath("$.observerUtilization.count").value(2));
    }

    @Test
    void listSufficiencyCountsListsFilledToTheListSize() throws Exception {
        int leander = signupOf(LEANDER);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM observer_match WHERE signup_id = ?", Integer.class, leander))
                .isEqualTo(ObserverEligibilityService.LIST_SIZE - 1);
        metrics()
                .andExpect(jsonPath("$.listSize").value(ObserverEligibilityService.LIST_SIZE))
                .andExpect(jsonPath("$.listSufficiency.count").value(0));

        db.update(
                "INSERT INTO observer_match (signup_id, observer_id, generated_date, match_status) VALUES (?, ?, DATE '2026-10-26', 'PROPOSED')",
                leander,
                observerOf(ARLO));
        metrics()
                .andExpect(jsonPath("$.listSufficiency.count").value(1))
                .andExpect(jsonPath("$.listSufficiency.total").value(8));
    }

    @Test
    void committeeTracksEachGeneratedSignupsObservation() throws Exception {
        String leander = "$.observations[?(@.observeeId == " + LEANDER + ")]";
        observations()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycleId").value(1))
                .andExpect(jsonPath("$.cycleLabel").value("Fall 2026"))
                .andExpect(jsonPath("$.observationDeadline").value("2026-11-15"))
                .andExpect(jsonPath("$.observations.length()").value(8))
                .andExpect(jsonPath(leander + ".observee").value("Leander Coldwell"))
                .andExpect(jsonPath(leander + ".observer").value("Keira Larkspur"))
                .andExpect(jsonPath(leander + ".observerId").value(KEIRA))
                .andExpect(jsonPath(leander + ".observationId")
                        .value(db.queryForObject(
                                "SELECT o.observation_id FROM observation o JOIN evaluation_signup es ON es.signup_id=o.signup_id JOIN observee oe ON oe.observee_id=es.observee_id WHERE oe.professor_id=? AND oe.cycle_id=1",
                                Integer.class,
                                LEANDER)))
                .andExpect(jsonPath(leander + ".course").value("CS 3345.001"))
                .andExpect(jsonPath(leander + ".date").value("2026-11-04"))
                .andExpect(jsonPath(leander + ".startTime").value("08:30:00"))
                .andExpect(jsonPath(leander + ".status").value("SCHEDULED"))
                .andExpect(jsonPath("$.observations[?(@.status == 'AWAITING_OBSERVER')]", hasSize(7)))
                .andExpect(
                        jsonPath("$.observations[?(@.status == 'AWAITING_OBSERVER')].observer", everyItem(nullValue())))
                .andExpect(jsonPath(
                        "$.observations[?(@.listReleased == true)].observeeId", containsInAnyOrder(LEANDER, AVERY)));
    }

    @Test
    void observationStatusFollowsTheDateAndCompletion() throws Exception {
        String leander = "$.observations[?(@.observeeId == " + LEANDER + ")].status";
        clock.set("2026-11-04T15:00:00Z");
        observations().andExpect(jsonPath(leander).value("SCHEDULED"));
        clock.set("2026-11-05T15:00:00Z");
        observations().andExpect(jsonPath(leander).value("MISSING"));

        db.update(
                "UPDATE observation SET status = 'COMPLETED', observee_signed = TRUE, completed_at = TIMESTAMP '2026-11-04 10:00:00' WHERE signup_id = ?",
                signupOf(LEANDER));
        observations()
                .andExpect(jsonPath(leander).value("COMPLETED"))
                .andExpect(jsonPath("$.observations[?(@.observeeId == " + LEANDER + ")].observeeSigned")
                        .value(true))
                .andExpect(jsonPath("$.observations[?(@.observeeId == " + LEANDER + ")].observerSigned")
                        .value(false))
                .andExpect(jsonPath("$.observations[?(@.observeeId == " + LEANDER + ")].completedAt")
                        .value("2026-11-04T10:00:00"));

        // A cancelled signup leaves the list, as it leaves the dashboard's counts.
        db.update("UPDATE evaluation_signup SET status = 'CANCELLED' WHERE signup_id = ?", signupOf(ARLO));
        observations().andExpect(jsonPath("$.observations.length()").value(7));
    }

    @Test
    void remindersGoToOutstandingObservationsAndReleasedLists() throws Exception {
        // Leander's scheduled observation reminds him and Keira. Avery's list is released but she has not asked
        // anyone. The other four lists are not released, so their observees cannot request anyone yet.
        mvc.perform(as(KAIA, post(REMINDERS)))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"sent\":3}", JsonCompareMode.STRICT));

        assertThat(remindersFor(LEANDER))
                .containsExactly(
                        "Reminder from the Assessment Committee: your observation of CS 3345.001 by Keira Larkspur is scheduled for Nov 4 at 08:30.");
        assertThat(remindersFor(KEIRA))
                .containsExactly(
                        "Reminder from the Assessment Committee: your observation of Leander Coldwell's CS 3345.001 is scheduled for Nov 4 at 08:30.");
        assertThat(remindersFor(AVERY))
                .singleElement()
                .asString()
                .startsWith("Reminder from the Assessment Committee: choose an observer for CS 3345.")
                .endsWith(" and send a request so the observation can happen by the observation deadline, Nov 15.");
        assertThat(remindersFor(ARLO)).isEmpty();
    }

    @Test
    void missingAndCompletedObservationsGetTheRightReminders() throws Exception {
        clock.set("2026-11-05T15:00:00Z");
        mvc.perform(as(KAIA, post(REMINDERS))).andExpect(jsonPath("$.sent").value(3));
        assertThat(remindersFor(KEIRA))
                .containsExactly(
                        "Reminder from the Assessment Committee: your observation of Leander Coldwell's CS 3345.001 was scheduled for Nov 4 at 08:30 and has not been completed. Complete it as soon as possible.");

        db.update("UPDATE observation SET status = 'COMPLETED' WHERE signup_id = ?", signupOf(LEANDER));
        mvc.perform(as(KAIA, post(REMINDERS))).andExpect(jsonPath("$.sent").value(1));
        assertThat(remindersFor(KEIRA)).hasSize(1);
    }

    @Test
    void anUndatedObservationIsAskedToBeScheduled() throws Exception {
        db.update(
                "UPDATE observation SET observation_date = NULL, start_time = NULL WHERE signup_id = ?",
                signupOf(LEANDER));
        mvc.perform(as(KAIA, post(REMINDERS))).andExpect(status().isOk());
        assertThat(remindersFor(LEANDER))
                .containsExactly(
                        "Reminder from the Assessment Committee: your observation of CS 3345.001 by Keira Larkspur does not have a date yet. Schedule it by the observation deadline, Nov 15.");
    }

    @Test
    void remindersLeaveOutATimeOrDeadlineThatIsNotSet() throws Exception {
        db.update("UPDATE observation SET start_time = NULL WHERE signup_id = ?", signupOf(LEANDER));
        db.update("UPDATE evaluation_cycle SET observation_deadline = NULL WHERE cycle_id = 1");
        mvc.perform(as(KAIA, post(REMINDERS))).andExpect(status().isOk());
        assertThat(remindersFor(LEANDER))
                .containsExactly(
                        "Reminder from the Assessment Committee: your observation of CS 3345.001 by Keira Larkspur is scheduled for Nov 4.");
        assertThat(remindersFor(AVERY))
                .singleElement()
                .asString()
                .endsWith(" and send a request so the observation can happen.");
    }

    @Test
    void onlyTheCommitteeCanTrackObservationsOrSendReminders() throws Exception {
        mvc.perform(get(OBSERVATIONS).with(user(DARIO))).andExpect(status().isForbidden());
        mvc.perform(get(OBSERVATIONS)).andExpect(status().isUnauthorized());
        mvc.perform(as(DARIO, post(REMINDERS))).andExpect(status().isForbidden());
        assertThat(remindersFor(LEANDER)).isEmpty();
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
        return db.queryForList(
                "SELECT message FROM observer_notification WHERE professor_id = ? AND message LIKE 'Reminder%' ORDER BY notification_id",
                String.class, professorId);
    }

    private ResultActions expectMissing(int count) throws Exception {
        return metrics()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missingObservations").value(count));
    }

    private int signupOf(int professorId) {
        return db.queryForObject("""
                SELECT es.signup_id FROM evaluation_signup es JOIN observee oe ON oe.observee_id = es.observee_id
                WHERE oe.professor_id = ? AND oe.cycle_id = 1
                """, Integer.class, professorId);
    }

    private int observerOf(int professorId) {
        return db.queryForObject(
                "SELECT observer_id FROM observer WHERE professor_id = ? AND cycle_id = 1", Integer.class, professorId);
    }

    @Test
    void deferredCasesAreTrackedSeparatelySkippedByRemindersAndVisibleInHistory() throws Exception {
        clock.set("2026-11-02T15:00:00Z");
        int signup = signupOf(27);
        matching.defer(signup, 12, "No graduate-level observers available");
        observations()
                .andExpect(
                        jsonPath("$.observations[?(@.observeeId == 27)].status").value("DEFERRED"))
                .andExpect(jsonPath("$.observations[?(@.observeeId == 27)].deferral.reason")
                        .value("No graduate-level observers available"))
                .andExpect(jsonPath("$.observations[?(@.observeeId == 27)].deferral.targetSemester")
                        .value("Spring 2027"));
        metrics()
                .andExpect(jsonPath("$.observerUtilization.total").value(7))
                .andExpect(jsonPath("$.observerUtilization.count").value(1))
                .andExpect(jsonPath("$.listSufficiency.total").value(7));
        db.update("UPDATE observer_list_state SET released_at='2026-11-02T09:00:00' WHERE signup_id=?", signup);
        int before =
                db.queryForObject("SELECT COUNT(*) FROM observer_notification WHERE professor_id=27", Integer.class);
        mvc.perform(as(KAIA, post(REMINDERS))).andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM observer_notification WHERE professor_id=27", Integer.class))
                .isEqualTo(before);
        clock.set("2027-01-20T15:00:00Z");
        mvc.perform(get("/api/professors/27/evaluations").with(user(KAIA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.cycleId == 1)].status").value("DEFERRED"))
                .andExpect(
                        jsonPath("$[?(@.cycleId == 1)].deferral.reason").value("No graduate-level observers available"))
                .andExpect(jsonPath("$[?(@.cycleId == 1)].deferral.deferredBy").value("Kaia Clearbrook"))
                .andExpect(jsonPath("$[?(@.cycleId == 1)].deferral.deferredAt").value("2026-11-02T09:00:00"))
                .andExpect(
                        jsonPath("$[?(@.cycleId == 1)].deferral.targetSemester").value("Spring 2027"));
    }
}
