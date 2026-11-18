package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.security.AccountDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DemoModeDisabledTests {
    @Autowired
    MockMvc mvc;

    @Autowired
    AccountDetailsService accountDetails;

    @Test
    void demoEndpointsDoNotExistUnlessDemoModeIsOn() throws Exception {
        var kaia = accountDetails.loadUserByUsername("kaia.clearbrook@example.edu");
        mvc.perform(get("/api/demo/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/demo/accounts").with(user(kaia))).andExpect(status().isNotFound());
    }
}
