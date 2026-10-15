package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.config.SecurityConfig;
import edu.utdallas.cs4485.assessmenthelper.service.EvaluationRecord;
import edu.utdallas.cs4485.assessmenthelper.service.EvaluationRecordService;
import edu.utdallas.cs4485.assessmenthelper.service.NotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EvaluationRecordController.class)
@Import(SecurityConfig.class)
class EvaluationRecordControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EvaluationRecordService service;

    @Test
    void listReturnsTheProfessorsEvaluations() throws Exception {
        when(service.findByProfessor(2))
                .thenReturn(List.of(
                        new EvaluationRecord(1, "Fall", 2026, "ACTIVE", 1, null),
                        new EvaluationRecord(2, "Spring", 2025, "COMPLETED", 1, LocalDateTime.of(2025, 3, 14, 16, 0))));

        mockMvc.perform(get("/api/professors/2/evaluations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].term").value("Fall"))
                .andExpect(jsonPath("$[0].lastObservationCompletedAt").isEmpty())
                .andExpect(jsonPath("$[1].status").value("COMPLETED"))
                .andExpect(jsonPath("$[1].observationCount").value(1))
                .andExpect(jsonPath("$[1].lastObservationCompletedAt").value("2025-03-14T16:00:00"));
    }

    @Test
    void unknownProfessorReturns404ProblemDetail() throws Exception {
        when(service.findByProfessor(99)).thenThrow(new NotFoundException("No professor with id 99"));

        mockMvc.perform(get("/api/professors/99/evaluations"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("No professor with id 99"));
    }

    @Test
    void nonNumericProfessorIdReturns400() throws Exception {
        mockMvc.perform(get("/api/professors/abc/evaluations"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
