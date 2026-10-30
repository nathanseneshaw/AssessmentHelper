package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationSignupRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserveeRepository;
import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import edu.utdallas.cs4485.assessmenthelper.service.ConflictException;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationService;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationService.Answer;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationTemplateService;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ObservationWorkflowIntegrationTests.FixedTime.class)
@Transactional
class ObservationWorkflowIntegrationTests {

    // 10:15:42 in Dallas on the day of Leander's observation. Every write in a test happens at this moment, unless the
    // test brings its own clock.
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
    private JdbcTemplate db;

    @Autowired
    private JsonMapper json;

    @Autowired
    private ObservationService service;

    @Autowired
    private ObservationTemplateService templates;

    @Autowired
    private EvaluationSignupRepository signups;

    @Autowired
    private ObserveeRepository observees;

    // From data.sql: Keira observes Leander's CS 3345.001 on 2026-11-04 at 08:30 and has not started yet. Kaia is on
    // the Assessment Committee in their school; Dario has no part in this observation.
    private AccountPrincipal keira;
    private AccountPrincipal leander;
    private AccountPrincipal kaia;
    private AccountPrincipal dario;
    private int observation;
    private List<Integer> questions;

    @BeforeEach
    void scheduledObservation() {
        keira = principal("keira.larkspur@example.edu", 1, Role.PROFESSOR);
        leander = principal("leander.coldwell@example.edu", 1, Role.PROFESSOR);
        kaia = principal("kaia.clearbrook@example.edu", 1, Role.AC, Role.PROFESSOR);
        dario = principal("dario.hollowell@example.edu", 1, Role.PROFESSOR);
        observation = db.queryForObject("""
                SELECT o.observation_id FROM observation o
                JOIN evaluation_signup es ON es.signup_id = o.signup_id JOIN observee oe ON oe.observee_id = es.observee_id
                JOIN professor p ON p.professor_id = oe.professor_id
                JOIN evaluation_cycle c ON c.cycle_id = oe.cycle_id JOIN semester s ON s.semester_id = c.semester_id
                WHERE p.email = 'leander.coldwell@example.edu' AND s.term = 'Fall' AND s.academic_year = 2026
                """, Integer.class);
        questions = db.queryForList("""
                SELECT q.criterion_id FROM observation_criterion q JOIN observation o ON o.template_id = q.template_id
                WHERE o.observation_id = ? AND q.selected=TRUE ORDER BY q.display_order
                """, Integer.class, observation);
    }

    @Test
    void observerFillsInAndSubmitsTheFormThenBothCanReadIt() throws Exception {
        Map<String, Object> evaluation = evaluationStatus();

        // The date and time agreed when the request was accepted are kept.
        start(keira)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.viewerRole").value("OBSERVER"))
                .andExpect(jsonPath("$.observationDate").value("2026-11-04"))
                .andExpect(jsonPath("$.startTime").value("08:30:00"))
                .andExpect(jsonPath("$.responses.length()").value(0))
                .andExpect(jsonPath("$.submittedAt").value(nullValue()))
                .andExpect(jsonPath("$.canStart").value(false))
                .andExpect(jsonPath("$.canEdit").value(true));
        read(leander).andExpect(status().isForbidden());

        // A draft may leave questions out. Comments are trimmed, and an answer with nothing in it is not stored.
        save(
                        keira,
                        answer(1, 4, "  Most students joined the pair discussion.  "),
                        answer(2, 3, null),
                        answer(6, null, "Clear examples."),
                        answer(7, null, "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.responses[*].criterionId").value(contains(q(1), q(2), q(6))))
                .andExpect(jsonPath("$.responses[0].rating").value(4))
                .andExpect(jsonPath("$.responses[0].comments").value("Most students joined the pair discussion."))
                .andExpect(jsonPath("$.responses[1].comments").value(nullValue()))
                .andExpect(jsonPath("$.responses[2].rating").value(nullValue()))
                .andExpect(jsonPath("$.responses[2].comments").value("Clear examples."));
        assertThat(stored()).hasSize(3);

        // Each save replaces the whole draft, so the answers left out are removed.
        save(keira, answer(1, 5, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responses.length()").value(1))
                .andExpect(jsonPath("$.responses[0].rating").value(5))
                .andExpect(jsonPath("$.responses[0].comments").value(nullValue()));
        assertThat(stored()).hasSize(1);

        // Every required question needs an answer; blank strengths do not count, but the suggestions are optional.
        List<Answer> withoutStrengths = new ArrayList<>(complete().subList(0, 5));
        withoutStrengths.add(answer(6, null, "  "));
        submit(keira, withoutStrengths)
                .andExpect(status().isBadRequest())
                .andExpect(detail("Answer every required question before submitting: Strengths observed."));
        submit(keira, List.of(answer(1, 4, null), answer(6, null, "Clear examples.")))
                .andExpect(status().isBadRequest())
                .andExpect(detail("Answer every required question before submitting: Preparedness & Organization, "
                        + "Content Presentation, Rigor & Depth, Pace & Time for Questions."));
        assertThat(statusOf()).isEqualTo("IN_PROGRESS");
        assertThat(stored()).hasSize(1);

        submit(keira, complete())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.viewerRole").value("OBSERVER"))
                .andExpect(jsonPath("$.submittedAt").value(NOW))
                .andExpect(jsonPath("$.responses[*].criterionId").value(contains(q(1), q(2), q(3), q(4), q(5), q(6))))
                .andExpect(jsonPath("$.canStart").value(false))
                .andExpect(jsonPath("$.canEdit").value(false));
        // The observer's signature columns record the submission, and the observee is asked to review and sign off.
        // Nothing is signed for the observee yet, and the evaluation itself is left as it was.
        Map<String, Object> row = db.queryForMap(
                "SELECT observer_signed, observee_signed, observee_signed_at, completed_at FROM observation WHERE observation_id = ?",
                observation);
        assertThat(row).containsEntry("OBSERVER_SIGNED", true).containsEntry("OBSERVEE_SIGNED", false);
        assertThat(row.get("OBSERVEE_SIGNED_AT")).isNull();
        assertThat(row.get("COMPLETED_AT")).isNull();
        assertThat(db.queryForObject(
                        "SELECT observer_signed_at FROM observation WHERE observation_id = ?",
                        LocalDateTime.class,
                        observation))
                .isEqualTo(LocalDateTime.parse(NOW));
        assertThat(notificationsTo(leander))
                .containsExactly(
                        "Keira Larkspur submitted the observation of CS 3345.001. Please review it and sign off.");
        assertThat(notificationsTo(keira)).isEmpty();
        assertThat(evaluationStatus()).isEqualTo(evaluation);

        // Now the observee reads the same answers, and the observer reopens them read-only. The committee only reads
        // a record once the observee has signed off.
        read(leander)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerRole").value("OBSERVEE"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submittedAt").value(NOW))
                .andExpect(jsonPath("$.responses.length()").value(6))
                .andExpect(jsonPath("$.canStart").value(false))
                .andExpect(jsonPath("$.canEdit").value(false))
                .andExpect(jsonPath("$.canSign").value(true))
                .andExpect(jsonPath("$.locked").value(false));
        read(keira)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerRole").value("OBSERVER"))
                .andExpect(jsonPath("$.responses.length()").value(6))
                .andExpect(jsonPath("$.canStart").value(false))
                .andExpect(jsonPath("$.canEdit").value(false));
        read(kaia).andExpect(status().isForbidden());
        read(dario).andExpect(status().isForbidden());
    }

    @Test
    void eachStepIsAllowedOnlyInItsOwnStatus() throws Exception {
        save(keira, answer(1, 4, null))
                .andExpect(status().isConflict())
                .andExpect(detail("Start the observation before recording answers."));
        submit(keira, complete())
                .andExpect(status().isConflict())
                .andExpect(detail("Start the observation before recording answers."));
        assertThat(stored()).isEmpty();

        // Starting a form that is already open changes nothing.
        start(keira).andExpect(status().isOk());
        save(keira, answer(1, 4, null)).andExpect(status().isOk());
        start(keira)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.observationDate").value("2026-11-04"))
                .andExpect(jsonPath("$.startTime").value("08:30:00"))
                .andExpect(jsonPath("$.responses.length()").value(1));

        submit(keira, complete()).andExpect(status().isOk());
        start(keira).andExpect(status().isConflict()).andExpect(detail("This observation has already been submitted."));
        save(keira, answer(1, 1, null))
                .andExpect(status().isConflict())
                .andExpect(detail("This observation has already been submitted."));
        submit(keira, complete())
                .andExpect(status().isConflict())
                .andExpect(detail("This observation has already been submitted."));
        assertThat(notificationsTo(leander)).hasSize(1);

        // Completed records from earlier cycles are just as final.
        db.update("UPDATE observation SET status = 'COMPLETED' WHERE observation_id = ?", observation);
        start(keira).andExpect(status().isConflict()).andExpect(detail("This observation has already been submitted."));
        save(keira, answer(1, 1, null)).andExpect(status().isConflict());
        submit(keira, complete()).andExpect(status().isConflict());
        assertThat(stored()).hasSize(6);
    }

    @Test
    void onlyTheObserverChangesTheForm() throws Exception {
        for (AccountPrincipal other : List.of(leander, kaia, dario)) {
            start(other).andExpect(status().isForbidden());
        }
        assertThat(statusOf()).isEqualTo("SCHEDULED");

        start(keira).andExpect(status().isOk());
        for (AccountPrincipal other : List.of(leander, kaia, dario)) {
            save(other, answer(1, 4, null)).andExpect(status().isForbidden());
            submit(other, complete()).andExpect(status().isForbidden());
        }
        assertThat(stored()).isEmpty();
        assertThat(statusOf()).isEqualTo("IN_PROGRESS");

        // Reading the submitted form gives the observee nothing to change.
        submit(keira, complete()).andExpect(status().isOk());
        start(leander).andExpect(status().isForbidden());
        save(leander, answer(1, 1, null)).andExpect(status().isForbidden());
        submit(leander, complete()).andExpect(status().isForbidden());
        assertThat(statusOf()).isEqualTo("SUBMITTED");
        assertThat(stored()).hasSize(6);
    }

    @Test
    void theObserveeSignsOffAfterTheObserverSubmitsButNeverEditsTheAnswers() throws Exception {
        start(keira).andExpect(status().isOk());
        submit(keira, complete()).andExpect(status().isOk());
        // The observee sees the form with the sign-off available, but can still not change the answers. Signing off
        // is covered in ObservationLockingIntegrationTests.
        read(leander).andExpect(status().isOk()).andExpect(jsonPath("$.canSign").value(true));
        read(keira).andExpect(status().isOk()).andExpect(jsonPath("$.canSign").value(false));
        assertThat(statusOf()).isEqualTo("SUBMITTED");
        assertThat(db.queryForObject(
                        "SELECT observee_signed FROM observation WHERE observation_id = ?", Boolean.class, observation))
                .isFalse();
    }

    @Test
    void invalidAnswersAreRejectedWithoutChangingTheDraft() throws Exception {
        start(keira).andExpect(status().isOk());
        save(keira, answer(1, 4, "Kept")).andExpect(status().isOk());
        List<Map<String, Object>> draft = stored();

        // A question from another form is not on this one, even though it exists.
        db.update("""
                INSERT INTO observation_template (template_name, version, created_date, active_status)
                VALUES ('Pilot Observation Form', 1, DATE '2026-09-01', FALSE)
                """);
        db.update("""
                INSERT INTO observation_criterion (template_id, criterion_text, question_type, required, display_order,
                    rating_min, rating_max)
                SELECT template_id, 'Pilot question', 'RATING', TRUE, 1, 1, 5 FROM observation_template
                WHERE template_name = 'Pilot Observation Form'
                """);
        int pilot = db.queryForObject(
                "SELECT criterion_id FROM observation_criterion WHERE criterion_text = 'Pilot question'",
                Integer.class);

        Map<String, List<Answer>> invalid = new LinkedHashMap<>();
        invalid.put("Question 99999 is not on this form.", List.of(new Answer(99999, 3, null)));
        invalid.put("Question " + pilot + " is not on this form.", List.of(new Answer(pilot, 3, null)));
        invalid.put("Rate \"Student Engagement\" from 1 to 5.", List.of(answer(1, 0, null)));
        invalid.put("Rate \"Pace & Time for Questions\" from 1 to 5.", List.of(answer(5, 6, null)));
        invalid.put("\"Strengths observed\" takes comments, not a rating.", List.of(answer(6, 3, "Clear examples.")));
        invalid.put(
                "\"Student Engagement\" is answered more than once.",
                List.of(answer(1, 4, null), answer(2, 4, null), answer(1, 5, null)));
        invalid.put(
                "Comments on \"Suggestions for improvement\" must be at most 4000 characters.",
                List.of(answer(7, null, "x".repeat(4001))));
        for (var entry : invalid.entrySet()) {
            save(keira, entry.getValue()).andExpect(status().isBadRequest()).andExpect(detail(entry.getKey()));
            submit(keira, entry.getValue()).andExpect(status().isBadRequest()).andExpect(detail(entry.getKey()));
        }

        // Bodies without a list of answers, or with a missing one, are bad requests too.
        String path = "/api/observations/" + observation;
        write(
                        keira,
                        put(path + "/responses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(detail("Send the form's responses."));
        write(
                        keira,
                        post(path + "/submit")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"responses\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(detail("Send the form's responses."));
        write(
                        keira,
                        put(path + "/responses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"responses\":[null]}"))
                .andExpect(status().isBadRequest())
                .andExpect(detail("Each response must name a question."));
        write(keira, put(path + "/responses").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        write(keira, post(path + "/submit").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        assertThat(stored()).isEqualTo(draft);
        assertThat(statusOf()).isEqualTo("IN_PROGRESS");

        // Exactly 4000 characters fit, counted after trimming.
        save(keira, answer(7, null, " " + "y".repeat(4000) + " ")).andExpect(status().isOk());
        assertThat(db.queryForObject(
                        "SELECT LENGTH(comments) FROM observation_response WHERE observation_id = ?",
                        Integer.class,
                        observation))
                .isEqualTo(4000);
    }

    @Test
    void startingAnUnscheduledObservationRecordsTodayAndTheCurrentMinute() throws Exception {
        db.update(
                "UPDATE observation SET status = 'ACCEPTED', observation_date = NULL, start_time = NULL WHERE observation_id = ?",
                observation);
        read(keira).andExpect(jsonPath("$.canStart").value(true));
        start(keira)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.observationDate").value("2026-11-04"))
                .andExpect(jsonPath("$.startTime").value("10:15:00"))
                .andExpect(jsonPath("$.canEdit").value(true));
    }

    @Test
    void startingBeforeThePlannedDayRecordsTodayAndTheCurrentMinute() {
        // A week before Leander's class, at 09:05:30 in Dallas: the observation is happening now, not on Nov 4.
        var weekEarly = new ObservationService(
                db,
                templates,
                signups,
                observees,
                Clock.fixed(Instant.parse("2026-10-28T14:05:30Z"), ZoneId.of("America/Chicago")));
        var started = weekEarly.start(observation, keira);
        assertThat(started.status()).isEqualTo(ObservationService.Status.IN_PROGRESS);
        assertThat(started.observationDate()).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(started.startTime()).isEqualTo(LocalTime.of(9, 5));
    }

    @Test
    void startingAfterThePlannedDayKeepsThePlannedDateAndTime() throws Exception {
        db.update("UPDATE observation SET observation_date = DATE '2026-11-02' WHERE observation_id = ?", observation);
        start(keira)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observationDate").value("2026-11-02"))
                .andExpect(jsonPath("$.startTime").value("08:30:00"));
    }

    @Test
    void unknownObservationsAreNotFoundAndWritesNeedTheCsrfToken() throws Exception {
        String answers = body(List.of(answer(1, 4, null)));
        write(keira, post("/api/observations/99999/start")).andExpect(status().isNotFound());
        write(
                        keira,
                        put("/api/observations/99999/responses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(answers))
                .andExpect(status().isNotFound());
        write(
                        keira,
                        post("/api/observations/99999/submit")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(answers))
                .andExpect(status().isNotFound());

        // Without the token the request is refused before it reaches the form.
        String path = "/api/observations/" + observation;
        mvc.perform(post(path + "/start").with(user(keira))).andExpect(status().isForbidden());
        mvc.perform(put(path + "/responses")
                        .with(user(keira))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answers))
                .andExpect(status().isForbidden());
        mvc.perform(post(path + "/submit")
                        .with(user(keira))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(complete())))
                .andExpect(status().isForbidden());
        assertThat(statusOf()).isEqualTo("SCHEDULED");
        assertThat(stored()).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void simultaneousSubmitsSubmitTheFormOnce() throws Exception {
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
            assertThat(stored()).hasSize(6);
            assertThat(notificationsTo(leander)).hasSize(1);
        } finally {
            // This test commits, so put the observation back the way data.sql has it. Its audit log rows stay because
            // the database won't delete them.
            db.update("DELETE FROM observation_response WHERE observation_id = ?", observation);
            db.update("DELETE FROM observer_notification WHERE created_at = TIMESTAMP '2026-11-04 10:15:42'");
            db.update(
                    "UPDATE observation SET status = 'SCHEDULED', observer_signed = FALSE, observer_signed_at = NULL WHERE observation_id = ?",
                    observation);
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
        return new Answer(q(displayOrder), rating, comments);
    }

    private int q(int displayOrder) {
        return questions.get(displayOrder - 1);
    }

    private ResultActions read(AccountPrincipal principal) throws Exception {
        return mvc.perform(get("/api/observations/" + observation).with(user(principal)));
    }

    private ResultActions start(AccountPrincipal principal) throws Exception {
        return write(principal, post("/api/observations/" + observation + "/start"));
    }

    private ResultActions save(AccountPrincipal principal, Answer... answers) throws Exception {
        return save(principal, List.of(answers));
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

    private static ResultMatcher detail(String message) {
        return jsonPath("$.detail").value(message);
    }

    private List<Map<String, Object>> stored() {
        return db.queryForList(
                "SELECT criterion_id, rating, comments FROM observation_response WHERE observation_id = ? ORDER BY criterion_id",
                observation);
    }

    private String statusOf() {
        return db.queryForObject("SELECT status FROM observation WHERE observation_id = ?", String.class, observation);
    }

    // The notifications sent at the test's fixed time; the seeded ones are all older.
    private List<String> notificationsTo(AccountPrincipal principal) {
        return db.queryForList(
                "SELECT message FROM observer_notification WHERE professor_id = ? AND created_at = ? ORDER BY notification_id",
                String.class,
                principal.user().professorId(),
                LocalDateTime.parse(NOW));
    }

    private Map<String, Object> evaluationStatus() {
        return db.queryForMap("""
                SELECT es.status AS signup_status, oe.status AS observee_status FROM observation o
                JOIN evaluation_signup es ON es.signup_id = o.signup_id
                JOIN observee oe ON oe.observee_id = es.observee_id WHERE o.observation_id = ?
                """, observation);
    }

    private AccountPrincipal principal(String email, int schoolId, Role... roles) {
        Map<String, Object> professor = db.queryForMap(
                "SELECT professor_id, first_name || ' ' || last_name AS name FROM professor WHERE email = ?", email);
        return new AccountPrincipal(
                new CurrentUser(
                        null,
                        ((Number) professor.get("PROFESSOR_ID")).intValue(),
                        schoolId,
                        (String) professor.get("NAME"),
                        email,
                        List.of(roles)),
                null,
                true);
    }
}
