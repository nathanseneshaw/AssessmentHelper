package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.config.SecurityConfig;
import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.ProfessorAccess;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import edu.utdallas.cs4485.assessmenthelper.service.EvaluationRecord;
import edu.utdallas.cs4485.assessmenthelper.service.EvaluationRecordService;
import edu.utdallas.cs4485.assessmenthelper.service.NotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EvaluationRecordController.class)
@Import(SecurityConfig.class)
class EvaluationRecordControllerTests {

    private static final AccountPrincipal PROFESSOR = new AccountPrincipal(
            new CurrentUser(2, 2, 1, "Test Professor", "test@univ.edu", List.of(Role.PROFESSOR)), null, true);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EvaluationRecordService service;

    @MockitoBean(name = "professorAccess")
    private ProfessorAccess professorAccess;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void allowAccess() {
        when(professorAccess.canView(any(), anyInt())).thenReturn(true);
    }

    @Test
    void listReturnsTheProfessorsEvaluations() throws Exception {
        when(service.findByProfessor(2))
                .thenReturn(List.of(
                        new EvaluationRecord(1, "Fall", 2026, "ACTIVE", 1, null),
                        new EvaluationRecord(2, "Spring", 2025, "COMPLETED", 1, LocalDateTime.of(2025, 3, 14, 16, 0))));

        mockMvc.perform(get("/api/professors/2/evaluations").with(user(PROFESSOR)))
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

        mockMvc.perform(get("/api/professors/99/evaluations").with(user(PROFESSOR)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("No professor with id 99"));
    }

    @Test
    void deniedAccessReturns403ProblemDetail() throws Exception {
        when(professorAccess.canView(any(), anyInt())).thenReturn(false);

        mockMvc.perform(get("/api/professors/1/evaluations").with(user(PROFESSOR)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("You do not have access to this resource."));
    }

    @Test
    void nonNumericProfessorIdReturns400() throws Exception {
        mockMvc.perform(get("/api/professors/abc/evaluations").with(user(PROFESSOR)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
