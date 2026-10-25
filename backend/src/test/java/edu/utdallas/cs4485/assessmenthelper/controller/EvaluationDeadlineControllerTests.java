package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.config.SecurityConfig;
import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.ProfessorAccess;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import edu.utdallas.cs4485.assessmenthelper.service.EvaluationDeadline;
import edu.utdallas.cs4485.assessmenthelper.service.EvaluationDeadlineService;
import edu.utdallas.cs4485.assessmenthelper.service.NotFoundException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EvaluationDeadlineController.class)
@Import(SecurityConfig.class)
class EvaluationDeadlineControllerTests {

    private static final AccountPrincipal PROFESSOR = new AccountPrincipal(
            new CurrentUser(2, 2, 1, "Test Professor", "test@univ.edu", List.of(Role.PROFESSOR)), null, true);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EvaluationDeadlineService service;

    @MockitoBean(name = "professorAccess")
    private ProfessorAccess professorAccess;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void allowAccess() {
        when(professorAccess.canView(any(), anyInt())).thenReturn(true);
    }

    @Test
    void returnsDeadline() throws Exception {
        when(service.findByProfessor(2))
                .thenReturn(new EvaluationDeadline(
                        2, "ASSISTANT", 1, LocalDate.of(2026, 3, 10), LocalDate.of(2027, 3, 10), false));

        mockMvc.perform(get("/api/professors/2/evaluation-deadline").with(user(PROFESSOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextDueDate").value("2027-03-10"))
                .andExpect(jsonPath("$.evaluationIntervalYears").value(1))
                .andExpect(jsonPath("$.overdue").value(false));
    }

    @Test
    void unknownProfessorReturns404() throws Exception {
        when(service.findByProfessor(9)).thenThrow(new NotFoundException("No professor with id 9"));

        mockMvc.perform(get("/api/professors/9/evaluation-deadline").with(user(PROFESSOR)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deniedAccessReturns403() throws Exception {
        when(professorAccess.canView(any(), anyInt())).thenReturn(false);

        mockMvc.perform(get("/api/professors/1/evaluation-deadline").with(user(PROFESSOR)))
                .andExpect(status().isForbidden());
    }
}
