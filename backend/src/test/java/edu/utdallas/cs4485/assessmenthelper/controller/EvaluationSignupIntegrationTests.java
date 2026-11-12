package edu.utdallas.cs4485.assessmenthelper.controller;

import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.inactive;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationCycle;
import edu.utdallas.cs4485.assessmenthelper.entity.Observation;
import edu.utdallas.cs4485.assessmenthelper.entity.TeachingHistory;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationCycleRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationSignupRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserveeRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.TeachingHistoryRepository;
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

    // From data.sql, all in the same school. Kaia is on the Assessment Committee. Hollis teaches CS 2336 sections
    // 284, 288 and 291 in Fall 2026 (semester 13) and has not signed up. Leander's Fall 2026 signup (33, section 294)
    // is matched with a scheduled observation; he also taught section 270 in Spring 2026 (semester 11).
    private static final AccountPrincipal KAIA = new AccountPrincipal(
            new CurrentUser(
                    1, 12, 1, "Kaia Clearbrook", "kaia.clearbrook@example.edu", List.of(Role.AC, Role.PROFESSOR)),
            null,
            true);
    private static final AccountPrincipal LEANDER = new AccountPrincipal(
            new CurrentUser(null, 13, 1, "Leander Coldwell", "leander.coldwell@example.edu", List.of(Role.PROFESSOR)),
            null,
            true);
    private static final AccountPrincipal HOLLIS = new AccountPrincipal(
            new CurrentUser(null, 9, 1, "Hollis Carrowmore", "hollis.carrowmore@example.edu", List.of(Role.PROFESSOR)),
            null,
            true);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProfessorRepository professors;

    @Autowired
    private EvaluationCycleRepository cycles;

    @Autowired
    private ObserverRepository observers;

    @Autowired
    private ObserveeRepository observees;

    @Autowired
    private EvaluationSignupRepository signups;

    @Autowired
    private ObservationRepository observations;

    @Autowired
    private TeachingHistoryRepository teachingHistory;

    @Test
    void listsCatalogAndCombinesSemesterCourseAndProfessorFilters() throws Exception {
        mvc.perform(as(HOLLIS, get("/api/semesters")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(23));
        mvc.perform(as(HOLLIS, get("/api/semesters?signupOpen=true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].semesterId").value(13));
        mvc.perform(as(HOLLIS, get("/api/courses")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(28));
        mvc.perform(as(HOLLIS, get("/api/sections?semesterId=13&courseId=2&professorId=9")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].sectionNumber").value("002"))
                .andExpect(jsonPath("$[2].location").value("ECSS 2.305"));
        mvc.perform(as(HOLLIS, get("/api/sections?semesterId=13&courseId=3&professorId=9")))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(as(HOLLIS, get("/api/sections?semesterId=13&professorId=38")))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(as(HOLLIS, get("/api/sections?semesterId=2")))
                .andExpect(jsonPath("$.length()").value(81));
        mvc.perform(as(HOLLIS, get("/api/sections?courseId=4")))
                .andExpect(jsonPath("$.length()").value(20));
    }

    @Test
    void createsReadsRetriesAndUpdatesOnePendingSignup() throws Exception {
        // Keep this editable-signup scenario without candidates; matching otherwise locks the course selection.
        observers.findByCycleId(1).forEach(o -> observers.save(inactive(o)));
        mvc.perform(as(HOLLIS, get("/api/professors/9/evaluation-signups/13"))).andExpect(status().isNoContent());
        long originalCount = signups.count();
        var first = mvc.perform(as(HOLLIS, put("/api/professors/9/evaluation-signups/13"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":284}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionId").value(284))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.signupDate").value("2026-10-21"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        var repeated = mvc.perform(as(HOLLIS, put("/api/professors/9/evaluation-signups/13"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":284}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(repeated).isEqualTo(first);
        mvc.perform(as(HOLLIS, put("/api/professors/9/evaluation-signups/13"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":291}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionId").value(291));
        mvc.perform(as(HOLLIS, get("/api/professors/9/evaluation-signups/13")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionId").value(291));
        assertThat(signups.count()).isEqualTo(originalCount + 1);
        assertThat(hollisObserveesInCycleOne()).isEqualTo(1);
    }

    @Test
    void rejectsAnotherProfessorsSectionWithoutWritingAnything() throws Exception {
        long originalCount = signups.count();
        mvc.perform(as(HOLLIS, put("/api/professors/9/evaluation-signups/13"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":298}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("You can only select a section you teach."));
        assertThat(signups.count()).isEqualTo(originalCount);
        assertThat(hollisObserveesInCycleOne()).isZero();
    }

    @Test
    void rejectsWrongSemesterAndUnknownOrMissingIds() throws Exception {
        mvc.perform(as(LEANDER, put("/api/professors/13/evaluation-signups/13"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":270}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(HOLLIS, put("/api/professors/9/evaluation-signups/13"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":99999}"))
                .andExpect(status().isNotFound());
        // Ids of other professors, known or not, are refused before any lookup.
        mvc.perform(as(HOLLIS, put("/api/professors/99999/evaluation-signups/13"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":284}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(HOLLIS, put("/api/professors/9/evaluation-signups/99999"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":284}"))
                .andExpect(status().isNotFound());
        mvc.perform(as(HOLLIS, put("/api/professors/9/evaluation-signups/13"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(HOLLIS, put("/api/professors/9/evaluation-signups/13"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsClosedOrAmbiguousCyclesAndInactiveProfessors() throws Exception {
        mvc.perform(as(LEANDER, put("/api/professors/13/evaluation-signups/11"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":270}"))
                .andExpect(status().isConflict());
        professors.save(inactive(professors.findById(9).orElseThrow()));
        mvc.perform(as(HOLLIS, put("/api/professors/9/evaluation-signups/13"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":284}"))
                .andExpect(status().isConflict());
        cycles.save(new EvaluationCycle(null, 13, null, null, null, "OPEN"));
        mvc.perform(as(LEANDER, get("/api/professors/13/evaluation-signups/13")))
                .andExpect(status().isConflict());
    }

    @Test
    void preservesMatchedSignupAndItsObservationButAllowsAnIdenticalRetry() throws Exception {
        teachingHistory.save(new TeachingHistory(null, 13, 284));
        mvc.perform(as(LEANDER, put("/api/professors/13/evaluation-signups/13"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":284}"))
                .andExpect(status().isConflict());
        mvc.perform(as(LEANDER, put("/api/professors/13/evaluation-signups/13"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":294}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signupId").value(33))
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.signupDate").value("2026-10-12"));
        assertThat(signups.findById(33).orElseThrow().sectionId()).isEqualTo(294);
        assertThat(observations.findBySignupIdIn(List.of(33)))
                .singleElement()
                .extracting(Observation::status)
                .isEqualTo("SCHEDULED");
    }

    @Test
    void onlyTheProfessorCanChangeTheirSignupButCommitteeMembersCanReadIt() throws Exception {
        long originalCount = signups.count();
        mvc.perform(as(LEANDER, get("/api/professors/9/evaluation-signups/13"))).andExpect(status().isForbidden());
        mvc.perform(as(LEANDER, put("/api/professors/9/evaluation-signups/13"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":284}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(KAIA, get("/api/professors/13/evaluation-signups/13")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MATCHED"));
        mvc.perform(as(KAIA, put("/api/professors/9/evaluation-signups/13"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":284}"))
                .andExpect(status().isForbidden());
        assertThat(signups.count()).isEqualTo(originalCount);
    }

    private long hollisObserveesInCycleOne() {
        return observees.findByProfessorId(9).stream()
                .filter(o -> o.cycleId() == 1)
                .count();
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
