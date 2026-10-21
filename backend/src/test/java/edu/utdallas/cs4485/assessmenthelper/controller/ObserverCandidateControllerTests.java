package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.config.SecurityConfig;
import edu.utdallas.cs4485.assessmenthelper.service.InvalidRequestException;
import edu.utdallas.cs4485.assessmenthelper.service.NotFoundException;
import edu.utdallas.cs4485.assessmenthelper.service.ObserverCandidate;
import edu.utdallas.cs4485.assessmenthelper.service.ObserverCandidateService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(ObserverCandidateController.class)
@Import(SecurityConfig.class)
class ObserverCandidateControllerTests {

    private static final Map<String, String> VALID_PARAMS =
            Map.of("courseNumber", "3345", "sectionNumber", "001", "professorName", "Priya Raman");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ObserverCandidateService service;

    @Test
    void listReturnsTheCandidates() throws Exception {
        when(service.findCandidates("CS 3345", "001", "Priya Raman"))
                .thenReturn(List.of(
                        new ObserverCandidate(4, "David", "Chen", "david.chen@utdallas.edu", "CS", 4000),
                        new ObserverCandidate(5, "Maria", "Lopez", "maria.lopez@utdallas.edu", "CS", 3000)));

        mockMvc.perform(get("/api/observer-candidates")
                        .param("courseNumber", "CS 3345")
                        .param("sectionNumber", "001")
                        .param("professorName", "Priya Raman"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].professorId").value(4))
                .andExpect(jsonPath("$[0].firstName").value("David"))
                .andExpect(jsonPath("$[0].lastName").value("Chen"))
                .andExpect(jsonPath("$[0].email").value("david.chen@utdallas.edu"))
                .andExpect(jsonPath("$[0].departmentCode").value("CS"))
                .andExpect(jsonPath("$[0].courseLevel").value(4000))
                .andExpect(jsonPath("$[1].courseLevel").value(3000));
    }

    @Test
    void noCandidatesReturnsAnEmptyList() throws Exception {
        when(service.findCandidates("3345", "001", "Priya Raman")).thenReturn(List.of());

        mockMvc.perform(get("/api/observer-candidates")
                        .param("courseNumber", "3345")
                        .param("sectionNumber", "001")
                        .param("professorName", "Priya Raman"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unknownSectionReturns404ProblemDetail() throws Exception {
        when(service.findCandidates("3345", "999", "Priya Raman"))
                .thenThrow(new NotFoundException("No section 999 of course 3345"));

        mockMvc.perform(get("/api/observer-candidates")
                        .param("courseNumber", "3345")
                        .param("sectionNumber", "999")
                        .param("professorName", "Priya Raman"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("No section 999 of course 3345"));
    }

    @ParameterizedTest(name = "missing {0}")
    @ValueSource(strings = {"courseNumber", "sectionNumber", "professorName"})
    void missingParameterReturns400ProblemDetail(String missing) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/observer-candidates");
        VALID_PARAMS.forEach((name, value) -> {
            if (!name.equals(missing)) {
                request.param(name, value);
            }
        });

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Required parameter '" + missing + "' is not present."));
        verifyNoInteractions(service);
    }

    @Test
    void noParametersReturns400ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/observer-candidates"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Required parameter 'courseNumber' is not present."));
        verifyNoInteractions(service);
    }

    @Test
    void invalidRequestReturns400ProblemDetail() throws Exception {
        when(service.findCandidates("abc", "001", "Priya Raman"))
                .thenThrow(new InvalidRequestException("courseNumber must be a number"));

        mockMvc.perform(get("/api/observer-candidates")
                        .param("courseNumber", "abc")
                        .param("sectionNumber", "001")
                        .param("professorName", "Priya Raman"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("courseNumber must be a number"));
    }
}
