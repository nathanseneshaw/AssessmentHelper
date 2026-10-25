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
import edu.utdallas.cs4485.assessmenthelper.service.NotFoundException;
import edu.utdallas.cs4485.assessmenthelper.service.ProfessorService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProfessorController.class)
@Import(SecurityConfig.class)
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
                .andExpect(jsonPath("$[0].professorId").value(1))
                .andExpect(jsonPath("$[0].departmentId").value(1))
                .andExpect(jsonPath("$[0].professorRank").value("ASSISTANT"))
                .andExpect(jsonPath("$[0].lastName").value("Sample"))
                .andExpect(jsonPath("$[0].hireDate").value("2021-08-16"))
                .andExpect(jsonPath("$[1].firstName").value("Blake"))
                .andExpect(jsonPath("$[1].email").value("blake.stub@test.edu"))
                .andExpect(jsonPath("$[1].activeStatus").value(false));
    }

    @Test
    void schoolWithoutProfessorsReturnsEmptyArray() throws Exception {
        when(service.findBySchool("EMPTY")).thenReturn(List.of());

        mockMvc.perform(get("/api/professors").param("school", "EMPTY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void missingSchoolReturns400ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/professors"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(containsString("school")));

        verifyNoInteractions(service);
    }

    @Test
    void emptySchoolReturns400ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/professors").param("school", ""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    void blankSchoolReturns400ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/professors").param("school", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    void unknownSchoolReturns404ProblemDetail() throws Exception {
        when(service.findBySchool("XYZ")).thenThrow(new NotFoundException("No school with code XYZ"));

        mockMvc.perform(get("/api/professors").param("school", "XYZ"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("No school with code XYZ"));
    }
}
