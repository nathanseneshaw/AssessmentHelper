package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ObservationReadIntegrationTests.FixedTime.class)
@Transactional
class ObservationReadIntegrationTests {

    @TestConfiguration
    static class FixedTime {
        @Bean
        @Primary
        Clock observationClock() {
            return Clock.fixed(Instant.parse("2026-10-27T15:00:00Z"), ZoneId.of("America/Chicago"));
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate db;

    // From data.sql: Keira observes Leander's CS 3345.001 on 2026-11-04 and has not started yet. In Fall 2025 Arlo
    // observed both Keira and Luca, and both forms are complete. Kaia is on the Assessment Committee in their school;
    // Dario has no part in any of these.
    private AccountPrincipal keira;
    private AccountPrincipal leander;
    private AccountPrincipal arlo;
    private AccountPrincipal luca;
    private AccountPrincipal kaia;
    private AccountPrincipal dario;
    private int scheduled;
    private int completed;
    private int keirasOwn;

    @BeforeEach
    void demoObservations() {
        keira = principal("keira.larkspur@example.edu", 1, Role.PROFESSOR);
        leander = principal("leander.coldwell@example.edu", 1, Role.PROFESSOR);
        arlo = principal("arlo.glimmerton@example.edu", 1, Role.PROFESSOR);
        luca = principal("luca.lindenhall@example.edu", 1, Role.PROFESSOR);
        kaia = principal("kaia.clearbrook@example.edu", 1, Role.AC, Role.PROFESSOR);
        dario = principal("dario.hollowell@example.edu", 1, Role.PROFESSOR);
        scheduled = observationOf("leander.coldwell@example.edu", "Fall", 2026);
        completed = observationOf("luca.lindenhall@example.edu", "Fall", 2025);
        keirasOwn = observationOf("keira.larkspur@example.edu", "Fall", 2025);
    }

    @Test
    void observerSeesTheScheduledFormAndMayStartIt() throws Exception {
        read(keira, "/api/observations/" + scheduled)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observationId").value(scheduled))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.viewerRole").value("OBSERVER"))
                .andExpect(
                        jsonPath("$.observee.professorId").value(leander.user().professorId()))
                .andExpect(jsonPath("$.observee.name").value("Leander Coldwell"))
                .andExpect(jsonPath("$.observee.department").value("Computer Science"))
                .andExpect(jsonPath("$.observer.professorId").value(keira.user().professorId()))
                .andExpect(jsonPath("$.observer.name").value("Keira Larkspur"))
                .andExpect(jsonPath("$.course.label").value("CS 3345.001"))
                .andExpect(jsonPath("$.course.courseName")
                        .value("Data Structures and Foundations of Algorithmic Analysis"))
                .andExpect(jsonPath("$.course.meetingDays").value("MW"))
                .andExpect(jsonPath("$.course.startTime").value("08:30:00"))
                .andExpect(jsonPath("$.course.endTime").value("09:45:00"))
                .andExpect(jsonPath("$.semesterLabel").value("Fall 2026"))
                .andExpect(jsonPath("$.cycleId").value(cycleOf("Fall", 2026)))
                .andExpect(jsonPath("$.observationDate").value("2026-11-04"))
                .andExpect(jsonPath("$.startTime").value("08:30:00"))
                .andExpect(jsonPath("$.template.criteria.length()").value(7))
                .andExpect(jsonPath("$.responses.length()").value(0))
                .andExpect(jsonPath("$.submittedAt").value(nullValue()))
                .andExpect(jsonPath("$.canStart").value(true))
                .andExpect(jsonPath("$.canEdit").value(false))
                // Nobody signs the form any more, so none of the signature fields are sent.
                .andExpect(jsonPath("$.observerSigned").doesNotExist())
                .andExpect(jsonPath("$.observeeSigned").doesNotExist())
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andExpect(jsonPath("$.canSign").doesNotExist());
    }

    @Test
    void formListsTheSevenQuestionsInOrder() throws Exception {
        int templateId = db.queryForObject(
                "SELECT template_id FROM observation_template WHERE template_name = 'Standard Classroom Observation'",
                Integer.class);
        read(keira, "/api/observations/" + scheduled)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.template.templateId").value(templateId))
                .andExpect(jsonPath("$.template.templateName").value("Standard Classroom Observation"))
                .andExpect(jsonPath("$.template.version").value(1))
                .andExpect(jsonPath("$.template.criteria[*].criterionText")
                        .value(contains(
                                "Student Engagement",
                                "Preparedness & Organization",
                                "Content Presentation",
                                "Rigor & Depth",
                                "Pace & Time for Questions",
                                "Strengths observed",
                                "Suggestions for improvement")))
                .andExpect(jsonPath("$.template.criteria[*].questionType")
                        .value(contains("RATING", "RATING", "RATING", "RATING", "RATING", "TEXT", "TEXT")))
                .andExpect(jsonPath("$.template.criteria[*].required")
                        .value(contains(true, true, true, true, true, true, false)))
                .andExpect(jsonPath("$.template.criteria[*].displayOrder").value(contains(1, 2, 3, 4, 5, 6, 7)))
                .andExpect(jsonPath("$.template.criteria[0].ratingMin").value(1))
                .andExpect(jsonPath("$.template.criteria[0].ratingMax").value(5))
                .andExpect(jsonPath("$.template.criteria[5].ratingMin").value(nullValue()))
                .andExpect(jsonPath("$.template.criteria[5].ratingMax").value(nullValue()));

        // Questions follow display order, not insertion order, and the form keeps its template once that is retired.
        db.update("UPDATE observation_criterion SET display_order = 8 WHERE criterion_text = 'Student Engagement'");
        db.update("UPDATE observation_template SET active_status = FALSE");
        read(keira, "/api/observations/" + scheduled)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.template.templateId").value(templateId))
                .andExpect(jsonPath("$.template.criteria[0].criterionText").value("Preparedness & Organization"))
                .andExpect(jsonPath("$.template.criteria[6].criterionText").value("Student Engagement"));
    }

    @Test
    void observeeSeesTheFormOnlyOnceTheObserverSubmits() throws Exception {
        read(leander, "/api/observations/" + scheduled).andExpect(status().isForbidden());

        // A started draft is still the observer's alone.
        db.update("UPDATE observation SET status = 'IN_PROGRESS' WHERE observation_id = ?", scheduled);
        db.update(
                "INSERT INTO observation_response (observation_id, criterion_id, rating, comments) VALUES (?, ?, 4, 'Draft')",
                scheduled,
                criterion("Student Engagement"));
        read(leander, "/api/observations/" + scheduled).andExpect(status().isForbidden());
        read(keira, "/api/observations/" + scheduled)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submittedAt").value(nullValue()))
                .andExpect(jsonPath("$.canStart").value(false))
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.responses[0].rating").value(4));

        // Submitted, both may read it and neither may change it.
        db.update("""
                UPDATE observation SET status = 'SUBMITTED', observer_signed = TRUE,
                observer_signed_at = TIMESTAMP '2026-11-04 10:00:00' WHERE observation_id = ?
                """, scheduled);
        read(leander, "/api/observations/" + scheduled)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerRole").value("OBSERVEE"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submittedAt").value("2026-11-04T10:00:00"))
                .andExpect(jsonPath("$.responses[0].criterionId").value(criterion("Student Engagement")))
                .andExpect(jsonPath("$.responses[0].comments").value("Draft"))
                .andExpect(jsonPath("$.canStart").value(false))
                .andExpect(jsonPath("$.canEdit").value(false));
        read(keira, "/api/observations/" + scheduled)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerRole").value("OBSERVER"))
                .andExpect(jsonPath("$.submittedAt").value("2026-11-04T10:00:00"))
                .andExpect(jsonPath("$.canStart").value(false))
                .andExpect(jsonPath("$.canEdit").value(false));
    }

    @Test
    void bothSidesReopenACompletedFormWithItsAnswers() throws Exception {
        // data.sql records no answers on the completed forms, so give this one a full set.
        db.update("""
                INSERT INTO observation_response (observation_id, criterion_id, rating, comments)
                SELECT o.observation_id, q.criterion_id, CASE WHEN q.question_type = 'RATING' THEN 4 END,
                    'Notes on ' || q.criterion_text
                FROM observation o JOIN observation_criterion q ON q.template_id = o.template_id
                WHERE o.observation_id = ? AND q.selected=TRUE
                """, completed);
        List<Map<String, Object>> stored = db.queryForList("""
                SELECT r.criterion_id, r.rating, r.comments FROM observation_response r
                JOIN observation_criterion q ON q.criterion_id = r.criterion_id
                WHERE r.observation_id = ? ORDER BY q.display_order
                """, completed);
        assertThat(stored).hasSize(7);
        read(arlo, "/api/observations/" + completed)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerRole").value("OBSERVER"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.observee.name").value("Luca Lindenhall"))
                .andExpect(jsonPath("$.observer.name").value("Arlo Glimmerton"))
                .andExpect(jsonPath("$.semesterLabel").value("Fall 2025"))
                .andExpect(jsonPath("$.submittedAt").value("2025-10-07T15:00:00"))
                .andExpect(jsonPath("$.responses[*].criterionId").value(contains(column(stored, "CRITERION_ID"))))
                .andExpect(jsonPath("$.responses[*].rating").value(contains(column(stored, "RATING"))))
                .andExpect(jsonPath("$.responses[*].comments").value(contains(column(stored, "COMMENTS"))))
                .andExpect(jsonPath("$.canStart").value(false))
                .andExpect(jsonPath("$.canEdit").value(false));
        read(luca, "/api/observations/" + completed)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerRole").value("OBSERVEE"))
                .andExpect(jsonPath("$.responses.length()").value(7))
                .andExpect(jsonPath("$.canStart").value(false))
                .andExpect(jsonPath("$.canEdit").value(false));
    }

    @Test
    void theCommitteeNeverSeesTheForm() throws Exception {
        for (String status : List.of("ACCEPTED", "SCHEDULED", "IN_PROGRESS", "SUBMITTED", "COMPLETED")) {
            db.update("UPDATE observation SET status = ? WHERE observation_id = ?", status, scheduled);
            read(kaia, "/api/observations/" + scheduled).andExpect(status().isForbidden());
            read(keira, "/api/observations/" + scheduled).andExpect(status().isOk());
        }
        read(kaia, "/api/observations/" + completed).andExpect(status().isForbidden());
        read(kaia, "/api/observations/" + keirasOwn).andExpect(status().isForbidden());
    }

    @Test
    void aCommitteeMemberWhoIsTheObserveeSeesTheirOwnFormAsTheObservee() throws Exception {
        // Kaia's own class is observed, so being on the committee must not show her the draft.
        db.update("""
                UPDATE observee SET professor_id = ? WHERE observee_id =
                (SELECT es.observee_id FROM evaluation_signup es JOIN observation o ON o.signup_id = es.signup_id
                WHERE o.observation_id = ?)
                """, kaia.user().professorId(), scheduled);
        db.update("UPDATE observation SET status = 'IN_PROGRESS' WHERE observation_id = ?", scheduled);
        read(kaia, "/api/observations/" + scheduled).andExpect(status().isForbidden());

        db.update("""
                UPDATE observation SET status = 'SUBMITTED', observer_signed = TRUE,
                observer_signed_at = TIMESTAMP '2026-11-04 10:00:00' WHERE observation_id = ?
                """, scheduled);
        read(kaia, "/api/observations/" + scheduled)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerRole").value("OBSERVEE"))
                .andExpect(jsonPath("$.observee.name").value("Kaia Clearbrook"))
                .andExpect(jsonPath("$.canEdit").value(false));
    }

    @Test
    void outsidersAreForbiddenAndUnknownIdsAreNotFound() throws Exception {
        read(dario, "/api/observations/" + scheduled).andExpect(status().isForbidden());
        read(dario, "/api/observations/" + completed).andExpect(status().isForbidden());
        read(dario, "/api/professors/" + keira.user().professorId() + "/observations")
                .andExpect(status().isForbidden());
        read(keira, "/api/observations/99999").andExpect(status().isNotFound());
        read(kaia, "/api/observations/99999").andExpect(status().isNotFound());
    }

    @Test
    void theCommitteeListAndTemplateEndpointsAreGone() throws Exception {
        read(kaia, "/api/observations").andExpect(status().isNotFound());
        read(kaia, "/api/observations?cycleId=" + cycleOf("Fall", 2025)).andExpect(status().isNotFound());
        read(dario, "/api/observation-templates/active").andExpect(status().isNotFound());
    }

    @Test
    void professorListShowsBothSidesNewestFirst() throws Exception {
        String path = "/api/professors/" + keira.user().professorId() + "/observations";
        read(keira, path)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].observationId").value(contains(scheduled, keirasOwn)))
                .andExpect(jsonPath("$[0].role").value("OBSERVER"))
                .andExpect(jsonPath("$[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$[0].observeeName").value("Leander Coldwell"))
                .andExpect(jsonPath("$[0].observerName").value("Keira Larkspur"))
                .andExpect(jsonPath("$[0].courseLabel").value("CS 3345.001"))
                .andExpect(jsonPath("$[0].semesterLabel").value("Fall 2026"))
                .andExpect(jsonPath("$[0].cycleId").value(cycleOf("Fall", 2026)))
                .andExpect(jsonPath("$[0].observationDate").value("2026-11-04"))
                .andExpect(jsonPath("$[0].startTime").value("08:30:00"))
                .andExpect(jsonPath("$[0].submittedAt").value(nullValue()))
                .andExpect(jsonPath("$[1].role").value("OBSERVEE"))
                .andExpect(jsonPath("$[1].status").value("COMPLETED"))
                .andExpect(jsonPath("$[1].observerName").value("Arlo Glimmerton"))
                .andExpect(jsonPath("$[1].semesterLabel").value("Fall 2025"))
                .andExpect(jsonPath("$[1].cycleId").value(cycleOf("Fall", 2025)))
                .andExpect(jsonPath("$[1].submittedAt").value("2025-10-28T15:00:00"))
                .andExpect(jsonPath("$[1].observeeSignedAt").doesNotExist())
                .andExpect(jsonPath("$[1].completedAt").doesNotExist());
        // The committee reads the same list from Keira's point of view.
        read(kaia, path)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].role").value(contains("OBSERVER", "OBSERVEE")));
        // Leander's observation is listed for him before he can open it.
        read(leander, "/api/professors/" + leander.user().professorId() + "/observations")
                .andExpect(jsonPath("$[*].observationId").value(contains(scheduled)))
                .andExpect(jsonPath("$[0].role").value("OBSERVEE"));

        // Arlo observed Luca on 2025-10-07 and Keira on 2025-10-28, so Keira's comes first.
        String arlos = "/api/professors/" + arlo.user().professorId() + "/observations";
        read(arlo, arlos)
                .andExpect(jsonPath("$[*].observationId").value(containsInRelativeOrder(keirasOwn, completed)));

        // An observation without a date yet, as when one has just been accepted, comes first.
        db.update("UPDATE observation SET observation_date = NULL WHERE observation_id = ?", completed);
        read(arlo, arlos).andExpect(jsonPath("$[0].observationId").value(completed));
    }

    private ResultActions read(AccountPrincipal principal, String path) throws Exception {
        return mvc.perform(get(path).with(user(principal)));
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

    private int observationOf(String observeeEmail, String term, int year) {
        return db.queryForObject("""
                SELECT o.observation_id FROM observation o
                JOIN evaluation_signup es ON es.signup_id = o.signup_id JOIN observee oe ON oe.observee_id = es.observee_id
                JOIN professor p ON p.professor_id = oe.professor_id
                JOIN evaluation_cycle c ON c.cycle_id = oe.cycle_id JOIN semester s ON s.semester_id = c.semester_id
                WHERE p.email = ? AND s.term = ? AND s.academic_year = ?
                """, Integer.class, observeeEmail, term, year);
    }

    private int cycleOf(String term, int year) {
        return db.queryForObject("""
                SELECT c.cycle_id FROM evaluation_cycle c JOIN semester s ON s.semester_id = c.semester_id
                WHERE s.term = ? AND s.academic_year = ?
                """, Integer.class, term, year);
    }

    private int criterion(String text) {
        return db.queryForObject(
                "SELECT criterion_id FROM observation_criterion WHERE criterion_text = ?", Integer.class, text);
    }

    private static Object[] column(List<Map<String, Object>> rows, String name) {
        return rows.stream().map(row -> row.get(name)).toArray();
    }
}
