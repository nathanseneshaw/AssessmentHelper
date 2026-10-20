package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.config.SecurityConfig;
import edu.utdallas.cs4485.assessmenthelper.service.EvaluationDeadline;
import edu.utdallas.cs4485.assessmenthelper.service.EvaluationDeadlineService;
import edu.utdallas.cs4485.assessmenthelper.service.NotFoundException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EvaluationDeadlineController.class)
@Import(SecurityConfig.class)
class EvaluationDeadlineControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EvaluationDeadlineService service;

    @Test
    void returnsDeadline() throws Exception {
        when(service.findByProfessor(2))
                .thenReturn(new EvaluationDeadline(
                        2, "ASSISTANT", 1, LocalDate.of(2026, 3, 10), LocalDate.of(2027, 3, 10), false));

        mockMvc.perform(get("/api/professors/2/evaluation-deadline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextDueDate").value("2027-03-10"))
                .andExpect(jsonPath("$.evaluationIntervalYears").value(1))
                .andExpect(jsonPath("$.overdue").value(false));
    }

    @Test
    void unknownProfessorReturns404() throws Exception {
        when(service.findByProfessor(9)).thenThrow(new NotFoundException("No professor with id 9"));

        mockMvc.perform(get("/api/professors/9/evaluation-deadline")).andExpect(status().isNotFound());
    }
}
