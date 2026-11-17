package edu.utdallas.cs4485.assessmenthelper.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.entity.UserAccount;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.UserAccountRepository;
import jakarta.servlet.http.Cookie;
import java.util.stream.Collectors;
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

    // From data.sql: Kaia is an AC member in Computer Science (ECS), Dario (professor 34) is a professor in Computer
    // Science, and Isla (professor 160) is in Mathematics (NSM).
    private static final String KAIA = "kaia.clearbrook@example.edu";
    private static final String KAIA_PASSWORD = "Committee123!";
    private static final String DARIO = "dario.hollowell@example.edu";
    private static final String DARIO_PASSWORD = "Faculty123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProfessorRepository professors;

    @Autowired
    private UserAccountRepository accounts;

    @Test
    void accountExistsForEveryProfessorWhenSeedDataIsLoaded() {
        var accountEmails = accounts.findAll().stream()
                .filter(account -> account.professorId() != null)
                .collect(Collectors.toMap(UserAccount::professorId, UserAccount::email));
        assertThat(professors.findAll())
                .allSatisfy(professor ->
                        assertThat(accountEmails.get(professor.professorId())).isEqualTo(professor.email()));
    }

    @Test
    void loginReturnsUserAndRolesWhenCredentialsAreCorrect() throws Exception {
        mockMvc.perform(signInRequest(DARIO, DARIO_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Dario Hollowell"))
                .andExpect(jsonPath("$.professorId").value(34))
                .andExpect(jsonPath("$.roles").value(contains("PROFESSOR")));
        // Committee members also have the professor role.
        mockMvc.perform(signInRequest(KAIA, KAIA_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(contains("AC", "PROFESSOR")));
    }

    @Test
    void loginReturns401WhenPasswordIsWrong() throws Exception {
        mockMvc.perform(signInRequest(DARIO, "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Incorrect email or password."));
    }

    @Test
    void loginReturns403WhenCsrfTokenIsMissing() throws Exception {
        mockMvc.perform(post("/api/auth/login").param("email", DARIO).param("password", DARIO_PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void apiReturns401WhenUserIsNotSignedIn() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Sign in to continue."));
    }

    @Test
    void sessionEndsOnlyWhenUserLogsOut() throws Exception {
        MockHttpSession session = signIn(DARIO, DARIO_PASSWORD);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professorId").value(34));
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
