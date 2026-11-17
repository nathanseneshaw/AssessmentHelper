package edu.utdallas.cs4485.assessmenthelper.controller;

import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.observationOf;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.only;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.principal;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.withPlan;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.withStatus;
import static java.util.Comparator.comparing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.entity.Observation;
import edu.utdallas.cs4485.assessmenthelper.entity.ObservationCriterion;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverNotification;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationCriterionRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationResponseRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverNotificationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRequestRepository;
import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import edu.utdallas.cs4485.assessmenthelper.service.ConflictException;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationService;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationService.Answer;
import edu.utdallas.cs4485.assessmenthelper.service.ObserverEligibilityService;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * The observer fills in and submits the form over HTTP. The form's rules (statuses, drafts, ratings, required
 * questions) are covered in ObservationFormAcceptanceTests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ObservationWorkflowIntegrationTests.FixedTime.class)
@Transactional
class ObservationWorkflowIntegrationTests {

    // 10:15:42 in Dallas on the day of Leander's observation. Every write in a test happens at this moment.
    private static final String NOW = "2026-11-04T10:15:42";

    @TestConfiguration
    static class FixedTime {
        @Bean
        @Primary
        Clock workflowClock() {
            return Clock.fixed(Instant.parse("2026-11-04T16:15:42Z"), ZoneId.of("America/Chicago"));
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcAggregateTemplate db;

    @Autowired
    private JsonMapper json;

    @Autowired
    private ObservationService service;

    @Autowired
    private ObservationRepository observations;

    @Autowired
    private ObservationCriterionRepository criteria;

    @Autowired
    private ObservationResponseRepository responses;

    @Autowired
    private ObserverNotificationRepository notifications;

    @Autowired
    private ObserverRequestRepository observerRequests;

    @Autowired
    private ObserverEligibilityService eligibility;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // From data.sql: Keira observes Leander's CS 3345.001 on 2026-11-04 at 08:30 and has not started yet. Dario has no
    // part in this observation.
    private AccountPrincipal keira;
    private AccountPrincipal leander;
    private AccountPrincipal dario;
    private int observation;
    private List<Integer> questions;

    @BeforeEach
    void scheduledObservation() {
        keira = principal(db, "keira.larkspur@example.edu", 1, Role.PROFESSOR);
        leander = principal(db, "leander.coldwell@example.edu", 1, Role.PROFESSOR);
        dario = principal(db, "dario.hollowell@example.edu", 1, Role.PROFESSOR);
        observation = observationOf(db, "leander.coldwell@example.edu", "Fall", 2026);
        questions = criteria
                .findByTemplateIdOrderByDisplayOrderAscCriterionIdAsc(current().templateId())
                .stream()
                .filter(ObservationCriterion::selected)
                .map(ObservationCriterion::criterionId)
                .toList();
    }

    @Test
    void observeeCanReadFormWhenObserverSubmitsIt() throws Exception {
        start(keira)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.canEdit").value(true));

        save(keira, List.of(answer(1, 4, "Most students joined the pair discussion.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responses.length()").value(1))
                .andExpect(jsonPath("$.responses[0].rating").value(4));

        submit(keira, complete())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submittedAt").value(NOW))
                .andExpect(jsonPath("$.responses.length()").value(6));

        read(leander)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerRole").value("OBSERVEE"))
                .andExpect(jsonPath("$.canSign").value(true));
    }

    @Test
    void formChangesReturn403WhenCallerIsNotTheObserver() throws Exception {
        start(keira).andExpect(status().isOk());

        save(leander, List.of(answer(1, 4, null))).andExpect(status().isForbidden());
        submit(dario, complete()).andExpect(status().isForbidden());
        assertThat(storedResponses()).isZero();
    }

    @Test
    void saveReturns400WhenAnswerIsForAQuestionNotOnTheForm() throws Exception {
        start(keira).andExpect(status().isOk());

        save(keira, List.of(new Answer(99999, 3, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Question 99999 is not on this form."));
        assertThat(storedResponses()).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void formIsSubmittedOnceWhenTwoSubmitsArriveTogether() throws Exception {
        Observation original = current();
        try {
            service.start(observation, keira);
            var ready = new CountDownLatch(2);
            var go = new CountDownLatch(1);
            Callable<String> submit = () -> {
                ready.countDown();
                if (!go.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                try {
                    return service.submit(observation, complete(), keira)
                            .status()
                            .name();
                } catch (ConflictException e) {
                    return e.getMessage();
                }
            };
            try (var executor = Executors.newFixedThreadPool(2)) {
                var first = executor.submit(submit);
                var second = executor.submit(submit);
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                go.countDown();
                assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                        .containsExactlyInAnyOrder("SUBMITTED", "This observation has already been submitted.");
            }
            assertThat(storedResponses()).isEqualTo(6);
            assertThat(notificationsTo(leander)).hasSize(1);
        } finally {
            restore(original);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void schedulingWhileTheObserverStartsWaitsForTheStartAndThenRefuses() throws Exception {
        Observation original = current();
        try {
            updateObservation(o -> withPlan(withStatus(o, "ACCEPTED"), null, null));
            int observer = keira.user().professorId();
            int request =
                    only(observerRequests.findBySignupId(original.signupId())).requestId();
            LocalDate date = eligibility.availableDates(observer, request).getFirst();
            var started = new CountDownLatch(1);
            var commit = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var start =
                        executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                            service.start(observation, keira);
                            started.countDown();
                            try {
                                if (!commit.await(5, TimeUnit.SECONDS))
                                    throw new IllegalStateException("Commit timed out");
                            } catch (InterruptedException e) {
                                throw new IllegalStateException(e);
                            }
                        }));
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                Callable<String> schedule = () -> {
                    try {
                        eligibility.scheduleObservation(observer, request, date);
                        return "SCHEDULED";
                    } catch (ConflictException e) {
                        return e.getMessage();
                    }
                };
                var scheduled = executor.submit(schedule);
                // Gives the schedule time to reach the observation row while the start still holds it, so that
                // scheduling from an unlocked read would overwrite the start once it commits.
                Thread.sleep(300);
                commit.countDown();
                start.get(10, TimeUnit.SECONDS);
                assertThat(scheduled.get(10, TimeUnit.SECONDS))
                        .isEqualTo("This observation has already been scheduled or started.");
            }
            assertThat(statusOf()).isEqualTo("IN_PROGRESS");
        } finally {
            restore(original);
        }
    }

    // A complete form: all five ratings, the required strengths, and no suggestions.
    private List<Answer> complete() {
        return List.of(
                answer(1, 4, "Most students joined the pair discussion."),
                answer(2, 5, null),
                answer(3, 4, "Each step of the worked example was explained aloud."),
                answer(4, 3, null),
                answer(5, 4, null),
                answer(6, null, "Clear explanations and an easy rapport with students."));
    }

    // An answer to the question at this position on the form.
    private Answer answer(int displayOrder, Integer rating, String comments) {
        return new Answer(questions.get(displayOrder - 1), rating, comments);
    }

    private ResultActions read(AccountPrincipal principal) throws Exception {
        return mvc.perform(get("/api/observations/" + observation).with(user(principal)));
    }

    private ResultActions start(AccountPrincipal principal) throws Exception {
        return write(principal, post("/api/observations/" + observation + "/start"));
    }

    private ResultActions save(AccountPrincipal principal, List<Answer> answers) throws Exception {
        return write(
                principal,
                put("/api/observations/" + observation + "/responses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(answers)));
    }

    private ResultActions submit(AccountPrincipal principal, List<Answer> answers) throws Exception {
        return write(
                principal,
                post("/api/observations/" + observation + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(answers)));
    }

    // Signs the request in and adds a CSRF token the way the frontend gets one (see
    // FacultyObserverRequestIntegrationTests for why the csrf() helper is not used).
    private ResultActions write(AccountPrincipal principal, MockHttpServletRequestBuilder request) throws Exception {
        Cookie token =
                mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return mvc.perform(request.with(user(principal)).cookie(token).header("X-XSRF-TOKEN", token.getValue()));
    }

    private String body(List<Answer> answers) {
        return json.writeValueAsString(new ObservationController.ResponsesBody(answers));
    }

    private int storedResponses() {
        return responses.findByObservationId(observation).size();
    }

    private Observation current() {
        return observations.findById(observation).orElseThrow();
    }

    private String statusOf() {
        return current().status();
    }

    private void updateObservation(UnaryOperator<Observation> change) {
        observations.save(change.apply(current()));
    }

    // The tests that commit put the observation back the way data.sql has it. Its audit log rows stay because the
    // database won't delete them.
    private void restore(Observation original) {
        responses.deleteAll(responses.findByObservationId(observation));
        notifications.deleteAll(notifications.findAll().stream()
                .filter(n -> n.createdAt().equals(LocalDateTime.parse(NOW)))
                .toList());
        observations.save(original);
    }

    // The notifications sent at the test's fixed time; the seeded ones are all older.
    private List<String> notificationsTo(AccountPrincipal principal) {
        return notifications.findByProfessorId(principal.user().professorId()).stream()
                .filter(n -> n.createdAt().equals(LocalDateTime.parse(NOW)))
                .sorted(comparing(ObserverNotification::notificationId))
                .map(ObserverNotification::message)
                .toList();
    }
}
