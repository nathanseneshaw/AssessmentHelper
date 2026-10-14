package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.config.SecurityConfig;
import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationPolicy;
import edu.utdallas.cs4485.assessmenthelper.service.EvaluationPolicyService;
import edu.utdallas.cs4485.assessmenthelper.service.NotFoundException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EvaluationPolicyController.class)
@Import(SecurityConfig.class)
class EvaluationPolicyControllerTests {

    private static final EvaluationPolicy ASSISTANT =
            new EvaluationPolicy("ASSISTANT", 1, true, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), 0L);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EvaluationPolicyService service;

    @Test
    void listReturnsAllPolicies() throws Exception {
        when(service.findAll()).thenReturn(List.of(ASSISTANT));

        mockMvc.perform(get("/api/evaluation-policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].professorRank").value("ASSISTANT"))
                .andExpect(jsonPath("$[0].evaluationIntervalYears").value(1));
    }

    @Test
    void unknownRankReturns404ProblemDetail() throws Exception {
        when(service.findByRank("EMERITUS")).thenThrow(new NotFoundException("No evaluation policy for rank EMERITUS"));

        mockMvc.perform(get("/api/evaluation-policies/EMERITUS"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("No evaluation policy for rank EMERITUS"));
    }

    @Test
    void frontendOriginIsAllowedByCors() throws Exception {
        mockMvc.perform(options("/api/evaluation-policies")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    @Test
    void otherOriginsAreRejectedByCors() throws Exception {
        mockMvc.perform(options("/api/evaluation-policies")
                        .header(HttpHeaders.ORIGIN, "http://example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }
}
