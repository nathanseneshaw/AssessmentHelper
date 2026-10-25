package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(EvaluationSignupIntegrationTests.FixedTime.class)
@Transactional
class EvaluationSignupIntegrationTests {
    @TestConfiguration
    static class FixedTime {
        @Bean
        @Primary
        Clock signupClock() {
            return Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneId.of("America/Chicago"));
        }
    }

    // Professors from test-data.sql. Alice is on the Assessment Committee; all three are in the same school.
    private static final AccountPrincipal ALICE = new AccountPrincipal(
            new CurrentUser(1, 1, 1, "Alice Alice", "alice@utdallas.edu", List.of(Role.AC, Role.PROFESSOR)),
            null,
            true);
    private static final AccountPrincipal BOB = new AccountPrincipal(
            new CurrentUser(2, 2, 1, "Bob Bob", "bob@utdallas.edu", List.of(Role.PROFESSOR)), null, true);
    private static final AccountPrincipal CAROL = new AccountPrincipal(
            new CurrentUser(3, 3, 1, "Carol Carol", "carol@utdallas.edu", List.of(Role.PROFESSOR)), null, true);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate db;

    @Test
    void listsCatalogAndCombinesSemesterCourseAndProfessorFilters() throws Exception {
        mvc.perform(as(CAROL, get("/api/semesters")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10));
        mvc.perform(as(CAROL, get("/api/semesters?signupOpen=true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].semesterId").value(1));
        mvc.perform(as(CAROL, get("/api/courses")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(as(CAROL, get("/api/sections?semesterId=1&courseId=1&professorId=3")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sectionNumber").value("002"));
        mvc.perform(as(CAROL, get("/api/sections?semesterId=1&courseId=2&professorId=3")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].location").value("ECS 2.410"));
        mvc.perform(as(CAROL, get("/api/sections?semesterId=1&professorId=1")))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(as(CAROL, get("/api/sections?semesterId=2")))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(as(CAROL, get("/api/sections?courseId=2")))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void createsReadsRetriesAndUpdatesOnePendingSignup() throws Exception {
        mvc.perform(as(CAROL, get("/api/professors/3/evaluation-signups/1"))).andExpect(status().isNoContent());
        int originalCount = countSignups();
        var first = mvc.perform(as(CAROL, put("/api/professors/3/evaluation-signups/1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionId").value(9))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.signupDate").value("2026-10-21"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        var repeated = mvc.perform(as(CAROL, put("/api/professors/3/evaluation-signups/1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":9}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(repeated).isEqualTo(first);
        mvc.perform(as(CAROL, put("/api/professors/3/evaluation-signups/1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":11}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionId").value(11));
        mvc.perform(as(CAROL, get("/api/professors/3/evaluation-signups/1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionId").value(11));
        assertThat(countSignups()).isEqualTo(originalCount + 1);
        assertThat(db.queryForObject(
                        "SELECT COUNT(*) FROM observee WHERE professor_id = 3 AND cycle_id = 1", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void rejectsAnotherProfessorsSectionWithoutWritingAnything() throws Exception {
        int originalCount = countSignups();
        mvc.perform(as(CAROL, put("/api/professors/3/evaluation-signups/1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("You can only select a section you teach."));
        assertThat(countSignups()).isEqualTo(originalCount);
        assertThat(db.queryForObject(
                        "SELECT COUNT(*) FROM observee WHERE professor_id = 3 AND cycle_id = 1", Integer.class))
                .isZero();
    }

    @Test
    void rejectsWrongSemesterAndUnknownOrMissingIds() throws Exception {
        mvc.perform(as(BOB, put("/api/professors/2/evaluation-signups/1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":2}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(CAROL, put("/api/professors/3/evaluation-signups/1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":99999}"))
                .andExpect(status().isNotFound());
        // Ids of other professors, known or not, are refused before any lookup.
        mvc.perform(as(CAROL, put("/api/professors/99999/evaluation-signups/1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":9}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(CAROL, put("/api/professors/3/evaluation-signups/99999"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":9}"))
                .andExpect(status().isNotFound());
        mvc.perform(as(CAROL, put("/api/professors/3/evaluation-signups/1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(CAROL, put("/api/professors/3/evaluation-signups/1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsClosedOrAmbiguousCyclesAndInactiveProfessors() throws Exception {
        mvc.perform(as(BOB, put("/api/professors/2/evaluation-signups/2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":2}"))
                .andExpect(status().isConflict());
        db.update("UPDATE professor SET active_status = FALSE WHERE professor_id = 3");
        mvc.perform(as(CAROL, put("/api/professors/3/evaluation-signups/1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":9}"))
                .andExpect(status().isConflict());
        db.update("INSERT INTO evaluation_cycle (semester_id, status) VALUES (1, 'OPEN')");
        mvc.perform(as(BOB, get("/api/professors/2/evaluation-signups/1"))).andExpect(status().isConflict());
    }

    @Test
    void preservesMatchedSignupAndItsObservationButAllowsAnIdenticalRetry() throws Exception {
        db.update("INSERT INTO teaching_history (professor_id, section_id) VALUES (2, 9)");
        mvc.perform(as(BOB, put("/api/professors/2/evaluation-signups/1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":9}"))
                .andExpect(status().isConflict());
        mvc.perform(as(BOB, put("/api/professors/2/evaluation-signups/1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signupId").value(1))
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.signupDate").value("2026-09-01"));
        assertThat(db.queryForObject("SELECT section_id FROM evaluation_signup WHERE signup_id = 1", Integer.class))
                .isEqualTo(1);
        assertThat(db.queryForObject("SELECT status FROM observation WHERE signup_id = 1", String.class))
                .isEqualTo("SCHEDULED");
    }

    @Test
    void onlyTheProfessorCanChangeTheirSignupButCommitteeMembersCanReadIt() throws Exception {
        int originalCount = countSignups();
        mvc.perform(as(BOB, get("/api/professors/3/evaluation-signups/1"))).andExpect(status().isForbidden());
        mvc.perform(as(BOB, put("/api/professors/3/evaluation-signups/1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":9}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(ALICE, get("/api/professors/2/evaluation-signups/1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MATCHED"));
        mvc.perform(as(ALICE, put("/api/professors/3/evaluation-signups/1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":9}"))
                .andExpect(status().isForbidden());
        assertThat(countSignups()).isEqualTo(originalCount);
    }

    private int countSignups() {
        return db.queryForObject("SELECT COUNT(*) FROM evaluation_signup", Integer.class);
    }

    // Signs the request in as the professor and adds a CSRF token the way the frontend gets one. The csrf() helper
    // is not used because it changes the shared Spring context (see AuthenticationTests).
    private MockHttpServletRequestBuilder as(AccountPrincipal professor, MockHttpServletRequestBuilder request)
            throws Exception {
        Cookie token =
                mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request.with(user(professor)).cookie(token).header("X-XSRF-TOKEN", token.getValue());
    }
}
