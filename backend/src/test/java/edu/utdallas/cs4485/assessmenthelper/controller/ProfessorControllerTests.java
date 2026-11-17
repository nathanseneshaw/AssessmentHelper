package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.config.SecurityConfig;
import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.service.ProfessorService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProfessorController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "AC")
class ProfessorControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfessorService service;

    @Test
    void listReturnsTheSchoolsProfessors() throws Exception {
        when(service.findBySchool("CS"))
                .thenReturn(List.of(
                        new Professor(
                                1,
                                1,
                                "ASSISTANT",
                                "Avery",
                                "Sample",
                                "avery.sample@test.edu",
                                LocalDate.of(2021, 8, 16),
                                true),
                        new Professor(2, 1, null, "Blake", "Stub", "blake.stub@test.edu", null, false)));

        mockMvc.perform(get("/api/professors").param("school", "CS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].lastName").value("Sample"))
                .andExpect(jsonPath("$[1].activeStatus").value(false));
    }

    @Test
    void missingSchoolReturns400ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/professors"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(containsString("school")));

        verifyNoInteractions(service);
    }
}
