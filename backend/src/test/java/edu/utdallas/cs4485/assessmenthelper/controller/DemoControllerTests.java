package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import edu.utdallas.cs4485.assessmenthelper.repository.UserAccountRepository;
import edu.utdallas.cs4485.assessmenthelper.service.DemoService;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

// Gets CSRF tokens the way the frontend does; AuthenticationTests explains why the csrf() helper is not used.
@SpringBootTest(properties = "app.demo.enabled=true")
@AutoConfigureMockMvc
@Import(DemoControllerTests.FixedTime.class)
@Transactional
class DemoControllerTests {
    @TestConfiguration
    static class FixedTime {
        @Bean
        @Primary
        Clock demoClock() {
            return Clock.fixed(Instant.parse("2026-11-18T15:00:00Z"), ZoneId.of("America/Chicago"));
        }
    }

    private static final String KAIA = "kaia.clearbrook@example.edu";
    private static final String DARIO = "dario.hollowell@example.edu";

    @Autowired
    MockMvc mvc;

    @Autowired
    UserAccountRepository accounts;

    MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) throws Exception {
        Cookie token =
                mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request.cookie(token).header("X-XSRF-TOKEN", token.getValue());
    }

    MockHttpServletRequestBuilder postJson(String path, String json) throws Exception {
        return withCsrf(MockMvcRequestBuilders.post(path))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    MockHttpSession signInAsDario() throws Exception {
        return signIn(DARIO, "Faculty123!");
    }

    MockHttpSession signIn(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(withCsrf(MockMvcRequestBuilders.post("/api/auth/login")
                        .param("email", email)
                        .param("password", password)))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);
    }

    @Test
    void anAddedProfessorCanSignInAndTeachAnAddedSection() throws Exception {
        MockHttpSession session = signInAsDario();
        String added = mvc.perform(postJson(
                                "/api/demo/professors",
                                "{\"firstName\":\"Jo\",\"lastName\":\"Demo\",\"departmentId\":1,\"rank\":\"ASSISTANT\"}")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jo.demo@example.edu"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        int professorId = JsonPath.read(added, "$.professorId");

        mvc.perform(postJson(
                                "/api/demo/sections",
                                "{\"professorId\":" + professorId + ",\"departmentId\":1,\"courseNumber\":\"4390\","
                                        + "\"courseName\":\"Computer Networks\",\"sectionNumber\":\"001\","
                                        + "\"meetingDays\":\"MW\",\"startTime\":\"10:00\",\"endTime\":\"11:15\"}")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("CS 4390.001"));

        MockHttpSession jo = signIn("jo.demo@example.edu", DemoService.PASSWORD);
        mvc.perform(get("/api/sections")
                        .param("professorId", String.valueOf(professorId))
                        .session(jo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sectionNumber").value("001"));
    }

    @Test
    void completingASignupWithoutAnObserverLocksASignedObservation() throws Exception {
        MockHttpSession session = signInAsDario();
        String open = mvc.perform(get("/api/demo/evaluations").session(session))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<Integer> avery = JsonPath.read(open, "$[?(@.observee == 'Avery Hazelwood')].signupId");

        String completed = mvc.perform(postJson("/api/demo/evaluations/" + avery.getFirst() + "/complete", "{}")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        int observationId = JsonPath.read(completed, "$.observationId");

        mvc.perform(get("/api/observations/" + observationId).session(signIn(KAIA, "Committee123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.locked").value(true));
    }

    @Test
    void switchingSignsInWithoutAPasswordAndSwitchesAgain() throws Exception {
        int kaia = accounts.findByEmailIgnoreCase(KAIA).orElseThrow().userId();
        int dario = accounts.findByEmailIgnoreCase(DARIO).orElseThrow().userId();

        MockHttpSession session =
                (MockHttpSession) mvc.perform(postJson("/api/demo/switch", "{\"userId\":" + kaia + "}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.roles").value(contains("AC", "PROFESSOR")))
                        .andReturn()
                        .getRequest()
                        .getSession(false);
        mvc.perform(postJson("/api/demo/switch", "{\"userId\":" + dario + "}").session(session))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(DARIO));
    }
}
