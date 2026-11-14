package edu.utdallas.cs4485.assessmenthelper.controller;

import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.matchOf;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.observerOf;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.only;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.signupOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import edu.utdallas.cs4485.assessmenthelper.entity.Observation;
import edu.utdallas.cs4485.assessmenthelper.entity.ObservationCriterion;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverMatch;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverNotification;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationCriterionRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserveeRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverMatchRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverNotificationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRequestRepository;
import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationService.Answer;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
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
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end tests of the sign-up, matching, observation and reporting workflows (issue #81). Each test drives the real
 * HTTP API as the people involved, one after another, and moves the clock through the Fall 2026 timetable (signup
 * deadline 2026-10-31, observation deadline 2026-11-15, feedback deadline 2026-12-01) instead of editing the cycle's
 * dates, so the deadlines themselves are exercised. Unlike the other integration tests, nothing is put in place
 * through the database except the clock.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(WorkflowEndToEndTests.Time.class)
@Transactional
class WorkflowEndToEndTests {

    private static final ZoneId DALLAS = ZoneId.of("America/Chicago");

    /** A clock the test moves forward between steps. */
    static class MutableClock extends Clock {
        private volatile Instant now = Instant.EPOCH;

        void set(String dallasLocalTime) {
            now = LocalDateTime.parse(dallasLocalTime).atZone(DALLAS).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return DALLAS;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TestConfiguration
    static class Time {
        @Bean
        @Primary
        MutableClock endToEndClock() {
            return new MutableClock();
        }
    }

    // From data.sql, all in the same school. Kaia is on the Assessment Committee. Dario teaches CS 3345.005 (section
    // 298) in Fall 2026 and has not signed up. The professors who can observe him once he does are Leander, Arlo,
    // Avery and Keira. Arlo and Keira are the two this file uses.
    private static final AccountPrincipal KAIA = new AccountPrincipal(
            new CurrentUser(
                    1, 12, 1, "Kaia Clearbrook", "kaia.clearbrook@example.edu", List.of(Role.AC, Role.PROFESSOR)),
            null,
            true);
    private static final AccountPrincipal DARIO = new AccountPrincipal(
            new CurrentUser(2, 34, 1, "Dario Hollowell", "dario.hollowell@example.edu", List.of(Role.PROFESSOR)),
            null,
            true);
    private static final AccountPrincipal ARLO = new AccountPrincipal(
            new CurrentUser(3, 26, 1, "Arlo Glimmerton", "arlo.glimmerton@example.edu", List.of(Role.PROFESSOR)),
            null,
            true);
    private static final AccountPrincipal KEIRA = new AccountPrincipal(
            new CurrentUser(null, 41, 1, "Keira Larkspur", "keira.larkspur@example.edu", List.of(Role.PROFESSOR)),
            null,
            true);

    private static final String DARIOS_ROW = "$.observations[?(@.observee == 'Dario Hollowell')]";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcAggregateTemplate db;

    @Autowired
    private ObserveeRepository observees;

    @Autowired
    private ObserverRepository observers;

    @Autowired
    private ObserverMatchRepository matches;

    @Autowired
    private ObserverRequestRepository requests;

    @Autowired
    private ObservationRepository observations;

    @Autowired
    private ObservationCriterionRepository criteria;

    @Autowired
    private ObserverNotificationRepository notifications;

    @Autowired
    private JsonMapper json;

    @Autowired
    private MutableClock clock;

    private int signupId;

    @BeforeEach
    void signupWindowIsOpen() {
        clock.set("2026-10-21T10:00:00");
    }

    @Test
    void signupThroughMatchingObservationAndReporting() throws Exception {
        // ---- Sign-up (before the 2026-10-31 signup deadline) ----
        mvc.perform(as(DARIO, get("/api/semesters?signupOpen=true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].semesterId").value(13));
        mvc.perform(as(DARIO, get("/api/sections?semesterId=13&professorId=34")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].sectionId").value(hasItem(298)));
        mvc.perform(as(DARIO, get("/api/professors/34/evaluation-signups/13"))).andExpect(status().isNoContent());
        signUp(DARIO, 34, 298);
        mvc.perform(as(DARIO, get("/api/professors/34/evaluation-signups/13")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionId").value(298))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.signupDate").value("2026-10-21"));
        signupId = signupOf(db, 34);

        // ---- Matching: the committee sees the generated list, Dario does not until it is released ----
        mvc.perform(as(KAIA, get("/api/observer-eligibility/ac")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signups[?(@.signupId == " + signupId + ")].candidates[*].name")
                        .value(containsInAnyOrder(
                                "Leander Coldwell", "Arlo Glimmerton", "Avery Hazelwood", "Keira Larkspur")));
        mvc.perform(as(DARIO, get("/api/professors/34/observer-requests")))
                .andExpect(jsonPath("$[0].candidates.length()").value(0))
                .andExpect(
                        jsonPath("$[0].unavailableReason").value("Your observer list is awaiting committee review."));
        mvc.perform(as(KAIA, post("/api/observer-eligibility/signups/" + signupId + "/release")))
                .andExpect(status().isConflict());

        // The signup deadline passes, and the committee sends the list.
        clock.set("2026-11-02T09:00:00");
        mvc.perform(as(KAIA, post("/api/observer-eligibility/signups/" + signupId + "/release")))
                .andExpect(status().isNoContent());
        mvc.perform(as(DARIO, get("/api/professors/34/observer-requests")))
                .andExpect(jsonPath("$[0].candidates.length()").value(4))
                .andExpect(jsonPath("$[0].unavailableReason").value(nullValue()))
                .andExpect(jsonPath("$[0].requests.length()").value(0));
        mvc.perform(as(KAIA, get("/api/observer-eligibility/ac")))
                .andExpect(jsonPath("$.signups[?(@.signupId == " + signupId + ")].released")
                        .value(true));
        mvc.perform(as(KAIA, get("/api/ac-dashboard/observations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath(DARIOS_ROW + ".status").value("AWAITING_OBSERVER"))
                .andExpect(jsonPath(DARIOS_ROW + ".listReleased").value(true));

        // Dario asks Arlo, who sees the request and accepts it.
        sendRequest(DARIO, 34, signupId, matchOf(db, signupId, 26))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestDate").value("2026-11-02T09:00:00"));
        String arloState = mvc.perform(as(ARLO, get("/api/observer-eligibility/faculty/26")))
                .andExpect(jsonPath("$.incoming.length()").value(1))
                .andExpect(jsonPath("$.incoming[0].observeeName").value("Dario Hollowell"))
                .andExpect(jsonPath("$.incoming[0].status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        int requestId = JsonPath.read(arloState, "$.incoming[0].requestId");
        respond(ARLO, 26, requestId, true).andExpect(status().isNoContent());

        mvc.perform(as(DARIO, get("/api/observer-eligibility/faculty/34")))
                .andExpect(jsonPath("$.outgoing[0].observerName").value("Arlo Glimmerton"))
                .andExpect(jsonPath("$.outgoing[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.notifications[0].message")
                        .value("Arlo Glimmerton accepted your observation request for CS 3345.005."));
        mvc.perform(as(DARIO, get("/api/professors/34/observer-requests")))
                .andExpect(jsonPath("$[0].unavailableReason")
                        .value("An observation is already scheduled for this signup."));
        // Accepting recorded the observation, and the committee's tracker now shows who observes whom.
        Observation scheduled = only(observationsOf(signupId));
        assertThat(scheduled.status()).isEqualTo("SCHEDULED");
        assertThat(observers.findById(scheduled.observerId()).orElseThrow().professorId())
                .isEqualTo(26);
        mvc.perform(as(KAIA, get("/api/ac-dashboard/observations")))
                .andExpect(jsonPath(DARIOS_ROW + ".observer").value("Arlo Glimmerton"))
                .andExpect(jsonPath(DARIOS_ROW + ".status").value("SCHEDULED"));

        // ---- Observation (on the day of the class) ----
        clock.set("2026-11-03T13:00:42");
        int observation = observationOf(DARIO, 34);
        mvc.perform(as(ARLO, get("/api/professors/26/observations")))
                .andExpect(jsonPath("$[?(@.observationId == " + observation + ")].role")
                        .value("OBSERVER"));

        act(ARLO, "/api/observations/" + observation + "/start")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.viewerRole").value("OBSERVER"))
                .andExpect(jsonPath("$.observationDate").value("2026-11-03"))
                .andExpect(jsonPath("$.startTime").value("13:00:00"));
        // Nobody else reads the form while it is being filled in.
        mvc.perform(as(DARIO, get("/api/observations/" + observation))).andExpect(status().isForbidden());
        mvc.perform(as(KAIA, get("/api/observations/" + observation))).andExpect(status().isForbidden());

        saveDraft(ARLO, observation, form(observation).subList(0, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.responses.length()").value(1));
        submit(ARLO, observation, List.of()).andExpect(status().isBadRequest()); // required questions are unanswered
        submit(ARLO, observation, form(observation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submittedAt").value("2026-11-03T13:00:42"));

        // The observer's answers are frozen. The observee reads them and signs off, completing and locking the record.
        saveDraft(ARLO, observation, form(observation).subList(0, 1)).andExpect(status().isConflict());
        mvc.perform(as(DARIO, get("/api/observations/" + observation)))
                .andExpect(jsonPath("$.viewerRole").value("OBSERVEE"))
                .andExpect(jsonPath("$.canSign").value(true))
                .andExpect(jsonPath("$.locked").value(false));
        mvc.perform(as(KAIA, get("/api/observations/" + observation))).andExpect(status().isForbidden());
        act(ARLO, "/api/observations/" + observation + "/observee-sign").andExpect(status().isForbidden());
        clock.set("2026-11-03T16:40:00");
        mvc.perform(as(DARIO, post("/api/observations/" + observation + "/observee-sign"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comments\":\"Fair and helpful, thank you.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.locked").value(true));
        mvc.perform(as(KAIA, get("/api/observations/" + observation)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerRole").value("COMMITTEE"))
                .andExpect(
                        jsonPath("$.responses.length()").value(form(observation).size()));
        act(ARLO, "/api/observations/" + observation + "/start").andExpect(status().isConflict());

        // ---- Reporting ----
        mvc.perform(as(KAIA, get("/api/ac-dashboard/observations")))
                .andExpect(jsonPath(DARIOS_ROW + ".status").value("COMPLETED"))
                .andExpect(jsonPath(DARIOS_ROW + ".date").value("2026-11-03"))
                .andExpect(jsonPath(DARIOS_ROW + ".observeeSigned").value(true))
                .andExpect(jsonPath(DARIOS_ROW + ".observerSigned").value(true))
                .andExpect(jsonPath(DARIOS_ROW + ".completedAt").value("2026-11-03T16:40:00"));
        mvc.perform(as(KAIA, get("/api/ac-dashboard/metrics")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycleLabel").value("Fall 2026"));
        mvc.perform(as(DARIO, get("/api/professors/34/evaluations")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].term").value("Fall"))
                .andExpect(jsonPath("$[0].academicYear").value(2026))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].observationCount").value(1))
                .andExpect(jsonPath("$[0].lastObservationCompletedAt").value("2026-11-03T16:40:00"));
        mvc.perform(as(DARIO, get("/api/professors/34/dashboard"))).andExpect(status().isOk());
        mvc.perform(as(ARLO, get("/api/professors/34/evaluations"))).andExpect(status().isForbidden());
        mvc.perform(as(KAIA, get("/api/observations/" + observation + "/audit-log")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].event")
                        .value(hasItems(
                                "STARTED", "EDITED", "SUBMITTED", "OBSERVEE_COMMENTED", "OBSERVEE_SIGNED", "LOCKED")))
                .andExpect(jsonPath("$[0].actorName").value("Arlo Glimmerton"));
        mvc.perform(as(DARIO, get("/api/observations/" + observation + "/audit-log")))
                .andExpect(status().isForbidden());

        // The committee can forward the locked record, and the forward is logged.
        act(KAIA, "/api/observations/" + observation + "/forward", "{\"recipientProfessorId\":41}")
                .andExpect(status().isNoContent());
        mvc.perform(as(KAIA, get("/api/observations/" + observation + "/audit-log")))
                .andExpect(jsonPath("$[*].event").value(hasItem("FORWARDED")));

        // ---- Feedback survey, once the observation period is over (before the 2026-12-01 feedback deadline) ----
        clock.set("2026-11-16T10:00:00");
        int cycleId = only(observees.findByProfessorId(34)).cycleId();
        mvc.perform(as(DARIO, get("/api/feedback-surveys")))
                .andExpect(jsonPath("$[0].cycleId").value(cycleId))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
        mvc.perform(as(DARIO, post("/api/feedback-surveys/" + cycleId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ratings\":[4,5,3,4,2,4],\"comments\":\"The process was clear.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
        mvc.perform(as(KAIA, get("/api/ac-dashboard/metrics")))
                .andExpect(jsonPath("$.surveyStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.surveyCompletion.count").value(1));
    }

    @Test
    void declinedRequestLeavesTheCommitteeToAssignAnObserver() throws Exception {
        signUp(DARIO, 34, 298);
        signupId = signupOf(db, 34);
        clock.set("2026-11-02T09:00:00");
        mvc.perform(as(KAIA, post("/api/observer-eligibility/signups/" + signupId + "/release")))
                .andExpect(status().isNoContent());

        // Arlo declines Dario's request, so there is no observation and the signup is still waiting.
        sendRequest(DARIO, 34, signupId, matchOf(db, signupId, 26)).andExpect(status().isCreated());
        int requestId = only(requests.findBySignupId(signupId)).requestId();
        respond(ARLO, 26, requestId, false).andExpect(status().isNoContent());
        mvc.perform(as(DARIO, get("/api/observer-eligibility/faculty/34")))
                .andExpect(jsonPath("$.outgoing[0].status").value("DECLINED"))
                .andExpect(jsonPath("$.notifications[0].message")
                        .value("Arlo Glimmerton declined your observation request for CS 3345.005."));
        assertThat(observationsOf(signupId)).isEmpty();
        mvc.perform(as(KAIA, get("/api/ac-dashboard/observations")))
                .andExpect(jsonPath(DARIOS_ROW + ".status").value("AWAITING_OBSERVER"));
        // A declined request cannot be answered a second time.
        respond(ARLO, 26, requestId, true).andExpect(status().isConflict());

        assign(41).andExpect(status().isConflict());

        // Let the other recommendations expire before the committee assigns Keira.
        int arlosObserverId = observerOf(db, 26);
        var remainingMatches = matches.findBySignupId(signupId).stream()
                .filter(m -> m.observerId() != arlosObserverId)
                .map(ObserverMatch::matchId)
                .sorted()
                .toList();
        for (int match : remainingMatches) {
            LocalDateTime expires = LocalDateTime.now(clock).plusMinutes(1);
            act(
                            DARIO,
                            "/api/professors/34/observer-requests",
                            "{\"signupId\":" + signupId + ",\"matchId\":" + match + ",\"expirationDate\":\"" + expires
                                    + "\"}")
                    .andExpect(status().isCreated());
            clock.set(expires.plusMinutes(1).toString());
        }

        // Keira carries the assigned observation through to submission.
        assign(41).andExpect(status().isNoContent());
        mvc.perform(as(KAIA, get("/api/ac-dashboard/observations")))
                .andExpect(jsonPath(DARIOS_ROW + ".observer").value("Keira Larkspur"))
                .andExpect(jsonPath(DARIOS_ROW + ".status").value("SCHEDULED"));
        mvc.perform(as(DARIO, get("/api/observer-eligibility/faculty/34")))
                .andExpect(jsonPath("$.notifications[0].message")
                        .value("The Assessment Committee assigned Keira Larkspur to observe CS 3345.005."));

        clock.set("2026-11-05T13:00:00");
        int observation = observationOf(DARIO, 34);
        act(ARLO, "/api/observations/" + observation + "/start").andExpect(status().isForbidden());
        act(KEIRA, "/api/observations/" + observation + "/start")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observationDate").value("2026-11-05"));
        submit(KEIRA, observation, form(observation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
        mvc.perform(as(KAIA, get("/api/ac-dashboard/observations")))
                .andExpect(jsonPath(DARIOS_ROW + ".status").value("PENDING_SIGN_OFF"))
                .andExpect(jsonPath(DARIOS_ROW + ".observerSigned").value(true))
                .andExpect(jsonPath(DARIOS_ROW + ".observeeSigned").value(false));
    }

    @Test
    void anUnsubmittedObservationIsMissingAfterTheCycleDeadlineAndRemindsOnlyItsObserver() throws Exception {
        signUp(DARIO, 34, 298);
        signupId = signupOf(db, 34);
        clock.set("2026-11-02T09:00:00");
        act(KAIA, "/api/observer-eligibility/signups/" + signupId + "/release").andExpect(status().isNoContent());
        String invitation = sendRequest(DARIO, 34, signupId, matchOf(db, signupId, 26))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        respond(ARLO, 26, JsonPath.read(invitation, "$.requestId"), true).andExpect(status().isNoContent());
        int observation = observationOf(DARIO, 34);
        clock.set("2026-11-03T13:00:00");
        act(ARLO, "/api/observations/" + observation + "/start").andExpect(status().isOk());

        // Passing the scheduled date does not make an unsubmitted form missing.
        clock.set("2026-11-04T09:00:00");
        mvc.perform(as(KAIA, get("/api/ac-dashboard/observations")))
                .andExpect(jsonPath(DARIOS_ROW + ".status").value("SCHEDULED"));
        assertThat(missingObservations()).isZero();
        // The deadline day remains on time; every incomplete observation is missing the following day.
        clock.set("2026-11-15T23:59:59");
        assertThat(missingObservations()).isZero();
        clock.set("2026-11-16T09:00:00");
        String tracking = mvc.perform(as(KAIA, get("/api/ac-dashboard/observations")))
                .andExpect(jsonPath(DARIOS_ROW + ".status").value("MISSING"))
                .andExpect(jsonPath(DARIOS_ROW + ".missingReason").value("Not submitted"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<?> missing = JsonPath.read(tracking, "$.observations[?(@.status == 'MISSING' && @.observerId != null)]");
        assertThat(missingObservations()).isEqualTo(missing.size()).isPositive();
        mvc.perform(as(KAIA, post("/api/ac-dashboard/observations/reminders"))).andExpect(status().isOk());
        assertThat(reminders(26, LocalDateTime.parse("2026-11-16T09:00:00")))
                .singleElement()
                .asString()
                .contains("complete and submit your observation of Dario Hollowell's CS 3345.005");
        assertThat(reminders(34, LocalDateTime.parse("2026-11-16T09:00:00"))).isEmpty();
        mvc.perform(as(DARIO, get("/api/professors/34/evaluations")))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].lastObservationCompletedAt").value(nullValue()));
    }

    // ---- Steps shared by the scenarios ----

    private void signUp(AccountPrincipal professor, int professorId, int sectionId) throws Exception {
        mvc.perform(as(professor, put("/api/professors/" + professorId + "/evaluation-signups/13"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":" + sectionId + "}"))
                .andExpect(status().isOk());
    }

    private ResultActions sendRequest(AccountPrincipal professor, int professorId, int signup, int match)
            throws Exception {
        return act(
                professor,
                "/api/professors/" + professorId + "/observer-requests",
                "{\"signupId\":" + signup + ",\"matchId\":" + match + "}");
    }

    private ResultActions respond(AccountPrincipal observer, int professorId, int requestId, boolean accept)
            throws Exception {
        return act(
                observer,
                "/api/observer-eligibility/faculty/" + professorId + "/requests/" + requestId + "/respond",
                accept ? "{\"accept\":true,\"observationDate\":\"2026-11-03\"}" : "{\"accept\":false}");
    }

    // The committee assigns the professor as observer of Dario's signup.
    private ResultActions assign(int observerProfessorId) throws Exception {
        return act(
                KAIA,
                "/api/observer-eligibility/signups/" + signupId + "/assign",
                "{\"observerId\":" + observerOf(db, observerProfessorId) + "}");
    }

    private ResultActions saveDraft(AccountPrincipal observer, int observation, List<Answer> answers) throws Exception {
        return mvc.perform(as(observer, put("/api/observations/" + observation + "/responses"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(answers)));
    }

    private ResultActions submit(AccountPrincipal observer, int observation, List<Answer> answers) throws Exception {
        return act(observer, "/api/observations/" + observation + "/submit", body(answers));
    }

    // ---- Lookups ----

    private int missingObservations() throws Exception {
        String metrics = mvc.perform(as(KAIA, get("/api/ac-dashboard/metrics")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(metrics, "$.missingObservations");
    }

    private List<Observation> observationsOf(int signup) {
        return observations.findBySignupIdIn(List.of(signup));
    }

    private List<String> reminders(int professorId, LocalDateTime createdAt) {
        return notifications.findByProfessorId(professorId).stream()
                .filter(n -> n.createdAt().equals(createdAt))
                .map(ObserverNotification::message)
                .filter(message -> message.startsWith("Reminder from the Assessment Committee:"))
                .toList();
    }

    // The observee finds the observation in their own list, as the faculty screens do.
    private int observationOf(AccountPrincipal observee, int professorId) throws Exception {
        String list = mvc.perform(as(observee, get("/api/professors/" + professorId + "/observations")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(list, "$[0].observationId");
    }

    // A complete form: every question on the observation's template, rated 4 or answered with a sentence.
    private List<Answer> form(int observation) {
        int templateId = observations.findById(observation).orElseThrow().templateId();
        return criteria.findByTemplateIdOrderByDisplayOrderAscCriterionIdAsc(templateId).stream()
                .filter(ObservationCriterion::selected)
                .map(q -> "RATING".equals(q.questionType())
                        ? new Answer(q.criterionId(), 4, null)
                        : new Answer(q.criterionId(), null, "Clear explanations and good rapport."))
                .toList();
    }

    private String body(List<Answer> answers) {
        return json.writeValueAsString(new ObservationController.ResponsesBody(answers));
    }

    private ResultActions act(AccountPrincipal principal, String url) throws Exception {
        return mvc.perform(as(principal, post(url)));
    }

    private ResultActions act(AccountPrincipal principal, String url, String content) throws Exception {
        return mvc.perform(
                as(principal, post(url)).contentType(MediaType.APPLICATION_JSON).content(content));
    }

    // Signs the request in as the person and adds a CSRF token the way the frontend gets one. The csrf() helper is not
    // used because it changes the shared Spring context (see AuthenticationTests).
    private MockHttpServletRequestBuilder as(AccountPrincipal principal, MockHttpServletRequestBuilder request)
            throws Exception {
        Cookie token =
                mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request.with(user(principal)).cookie(token).header("X-XSRF-TOKEN", token.getValue());
    }
}
