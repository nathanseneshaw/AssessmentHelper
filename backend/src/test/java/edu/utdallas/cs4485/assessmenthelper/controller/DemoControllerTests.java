package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.repository.UserAccountRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

// Gets CSRF tokens the way the frontend does; AuthenticationTests explains why the csrf() helper is not used.
@SpringBootTest(properties = "app.demo.enabled=true")
@AutoConfigureMockMvc
@Transactional
class DemoControllerTests {
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
