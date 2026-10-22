package edu.utdallas.cs4485.assessmenthelper.security;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// These tests get CSRF tokens the way the frontend does instead of using spring security test's csrf() or
// formLogin() helpers. Those helpers swap the cookie token repository for a session-based one in the shared
// Spring context, which would stop /api/auth/csrf from setting its cookie in every test that runs after them.
@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationTests {

    // Seed data: Alice is an AC member in Computer Science (ECS), Bob is a professor in Computer Science,
    // and Yara (professor 25) is in Mathematics (NSM).
    private static final String ALICE = "alice@utdallas.edu";
    private static final String ALICE_PASSWORD = "Committee123!";
    private static final String BOB = "bob@utdallas.edu";
    private static final String BOB_PASSWORD = "Faculty123!";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void signingInReturnsTheUser() throws Exception {
        mockMvc.perform(signInRequest(BOB, BOB_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bob Bob"))
                .andExpect(jsonPath("$.email").value(BOB))
                .andExpect(jsonPath("$.professorId").value(2))
                .andExpect(jsonPath("$.schoolId").value(1))
                .andExpect(jsonPath("$.roles").value(contains("PROFESSOR")));
    }

    @Test
    void committeeMembersAlsoHaveTheProfessorRole() throws Exception {
        mockMvc.perform(signInRequest(ALICE, ALICE_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(contains("AC", "PROFESSOR")));
    }

    @Test
    void emailIsNotCaseSensitive() throws Exception {
        mockMvc.perform(signInRequest("Bob@UTDallas.edu", BOB_PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordReturns401ProblemDetail() throws Exception {
        mockMvc.perform(signInRequest(BOB, "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Incorrect email or password."));
    }

    @Test
    void unknownEmailGetsTheSameErrorAsAWrongPassword() throws Exception {
        mockMvc.perform(signInRequest("nobody@utdallas.edu", BOB_PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Incorrect email or password."));
    }

    @Test
    void signingInWithoutACsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login").param("email", BOB).param("password", BOB_PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfEndpointSetsATokenCookieThatScriptsCanRead() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

    @Test
    void apiRequiresSignIn() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Sign in to continue."));
        mockMvc.perform(get("/api/evaluation-policies")).andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsTheSignedInUser() throws Exception {
        MockHttpSession session = signIn(BOB, BOB_PASSWORD);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bob Bob"))
                .andExpect(jsonPath("$.professorId").value(2));
    }

    @Test
    void professorsCanReadOnlyTheirOwnEvaluations() throws Exception {
        MockHttpSession session = signIn(BOB, BOB_PASSWORD);

        mockMvc.perform(get("/api/professors/2/evaluations").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/professors/1/evaluations").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void committeeMembersCanReadEvaluationsOnlyInTheirOwnSchool() throws Exception {
        MockHttpSession session = signIn(ALICE, ALICE_PASSWORD);

        mockMvc.perform(get("/api/professors/2/evaluations").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/professors/25/evaluations").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void signingOutEndsTheSession() throws Exception {
        MockHttpSession session = signIn(BOB, BOB_PASSWORD);

        mockMvc.perform(withCsrfToken(post("/api/auth/logout").session(session)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder withCsrfToken(MockHttpServletRequestBuilder request) throws Exception {
        Cookie token =
                mockMvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request.cookie(token).header("X-XSRF-TOKEN", token.getValue());
    }

    private MockHttpServletRequestBuilder signInRequest(String email, String password) throws Exception {
        return withCsrfToken(post("/api/auth/login").param("email", email).param("password", password));
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        return (MockHttpSession) mockMvc.perform(signInRequest(email, password))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);
    }
}
