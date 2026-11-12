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
import edu.utdallas.cs4485.assessmenthelper.service.NotFoundException;
import edu.utdallas.cs4485.assessmenthelper.service.TeachingHistoryEntry;
import edu.utdallas.cs4485.assessmenthelper.service.TeachingHistoryService;
import java.time.LocalDate;
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

@WebMvcTest(TeachingHistoryController.class)
@Import(SecurityConfig.class)
class TeachingHistoryControllerTests {

    private static final AccountPrincipal PROFESSOR = new AccountPrincipal(
            new CurrentUser(2, 2, 1, "Test Professor", "test@univ.edu", List.of(Role.PROFESSOR)), null, true);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TeachingHistoryService service;

    @MockitoBean(name = "professorAccess")
    private ProfessorAccess professorAccess;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void allowAccess() {
        when(professorAccess.canView(any(), anyInt())).thenReturn(true);
    }

    @Test
    void listReturnsTheProfessorsTeachingHistory() throws Exception {
        when(service.findByProfessor(2))
                .thenReturn(List.of(
                        new TeachingHistoryEntry(
                                10,
                                4,
                                "CS",
                                "4485",
                                "Computer Science Project",
                                4000,
                                "001",
                                "Fall",
                                2026,
                                LocalDate.of(2026, 8, 24),
                                LocalDate.of(2026, 12, 16)),
                        new TeachingHistoryEntry(
                                11, 5, "CS", "4V95", "Special Topics", null, "0H1", "Spring", 2025, null, null)));

        mockMvc.perform(get("/api/professors/2/teaching-history").with(user(PROFESSOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sectionId").value(10))
                .andExpect(jsonPath("$[0].courseCode").value("CS"))
                .andExpect(jsonPath("$[0].courseNumber").value("4485"))
                .andExpect(jsonPath("$[0].courseName").value("Computer Science Project"))
                .andExpect(jsonPath("$[0].level").value(4000))
                .andExpect(jsonPath("$[0].sectionNumber").value("001"))
                .andExpect(jsonPath("$[0].term").value("Fall"))
                .andExpect(jsonPath("$[0].academicYear").value(2026))
                .andExpect(jsonPath("$[0].semesterStartDate").value("2026-08-24"))
                .andExpect(jsonPath("$[0].semesterEndDate").value("2026-12-16"))
                .andExpect(jsonPath("$[1].level").isEmpty())
                .andExpect(jsonPath("$[1].semesterStartDate").isEmpty());
    }

    @Test
    void professorWhoNeverTaughtReturnsEmptyArray() throws Exception {
        when(service.findByProfessor(2)).thenReturn(List.of());

        mockMvc.perform(get("/api/professors/2/teaching-history").with(user(PROFESSOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void unknownProfessorReturns404ProblemDetail() throws Exception {
        when(service.findByProfessor(99)).thenThrow(new NotFoundException("No professor with id 99"));

        mockMvc.perform(get("/api/professors/99/teaching-history").with(user(PROFESSOR)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("No professor with id 99"));
    }

    @Test
    void deniedAccessReturns403ProblemDetail() throws Exception {
        when(professorAccess.canView(any(), anyInt())).thenReturn(false);

        mockMvc.perform(get("/api/professors/1/teaching-history").with(user(PROFESSOR)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("You do not have access to this resource."));
    }

    @Test
    void nonNumericProfessorIdReturns400() throws Exception {
        mockMvc.perform(get("/api/professors/abc/teaching-history").with(user(PROFESSOR)))
                .andExpect(status().isBadRequest());
    }
}
