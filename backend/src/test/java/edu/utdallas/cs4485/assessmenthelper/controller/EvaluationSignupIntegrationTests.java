package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate db;

    @Test
    void listsCatalogAndCombinesSemesterCourseAndProfessorFilters() throws Exception {
        mvc.perform(get("/api/semesters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10));
        mvc.perform(get("/api/semesters?signupOpen=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].semesterId").value(1));
        mvc.perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/sections?semesterId=1&courseId=1&professorId=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sectionNumber").value("002"));
        mvc.perform(get("/api/sections?semesterId=1&courseId=2&professorId=3"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].location").value("ECS 2.410"));
        mvc.perform(get("/api/sections?semesterId=1&professorId=1"))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/sections?semesterId=2"))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/sections?courseId=2"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void createsReadsRetriesAndUpdatesOnePendingSignup() throws Exception {
        mvc.perform(get("/api/professors/3/evaluation-signups/1")).andExpect(status().isNoContent());
        int originalCount = countSignups();
        var first = mvc.perform(put("/api/professors/3/evaluation-signups/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionId").value(9))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.signupDate").value("2026-10-21"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        var repeated = mvc.perform(put("/api/professors/3/evaluation-signups/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":9}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(repeated).isEqualTo(first);
        mvc.perform(put("/api/professors/3/evaluation-signups/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":11}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionId").value(11));
        mvc.perform(get("/api/professors/3/evaluation-signups/1"))
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
        mvc.perform(put("/api/professors/3/evaluation-signups/1")
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
        mvc.perform(put("/api/professors/2/evaluation-signups/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":2}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/professors/3/evaluation-signups/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":99999}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/professors/99999/evaluation-signups/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":9}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/professors/3/evaluation-signups/99999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":9}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/professors/3/evaluation-signups/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/professors/3/evaluation-signups/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsClosedOrAmbiguousCyclesAndInactiveProfessors() throws Exception {
        mvc.perform(put("/api/professors/2/evaluation-signups/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":2}"))
                .andExpect(status().isConflict());
        db.update("UPDATE professor SET active_status = FALSE WHERE professor_id = 3");
        mvc.perform(put("/api/professors/3/evaluation-signups/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":9}"))
                .andExpect(status().isConflict());
        db.update("INSERT INTO evaluation_cycle (semester_id, status) VALUES (1, 'OPEN')");
        mvc.perform(get("/api/professors/2/evaluation-signups/1")).andExpect(status().isConflict());
    }

    @Test
    void preservesMatchedSignupAndItsObservationButAllowsAnIdenticalRetry() throws Exception {
        db.update("INSERT INTO teaching_history (professor_id, section_id) VALUES (2, 9)");
        mvc.perform(put("/api/professors/2/evaluation-signups/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":9}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/professors/2/evaluation-signups/1")
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

    private int countSignups() {
        return db.queryForObject("SELECT COUNT(*) FROM evaluation_signup", Integer.class);
    }
}
