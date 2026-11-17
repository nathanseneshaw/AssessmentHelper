package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.config.SecurityConfig;
import edu.utdallas.cs4485.assessmenthelper.entity.CourseSection;
import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationCycle;
import edu.utdallas.cs4485.assessmenthelper.entity.Semester;
import edu.utdallas.cs4485.assessmenthelper.repository.CourseRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.CourseSectionRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationCycleRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.SemesterRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CourseCatalogController.class)
@Import(SecurityConfig.class)
@WithMockUser
class CourseCatalogControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SemesterRepository semesters;

    @MockitoBean
    private CourseRepository courses;

    @MockitoBean
    private CourseSectionRepository sections;

    @MockitoBean
    private EvaluationCycleRepository cycles;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void threeSemestersWithOneOpenCycle() {
        when(semesters.findAll())
                .thenReturn(List.of(
                        new Semester(1, "Fall", 2025, LocalDate.of(2025, 8, 25), LocalDate.of(2025, 12, 16)),
                        new Semester(2, "Summer", 2027, null, null),
                        new Semester(3, "Fall", 2026, LocalDate.of(2026, 8, 24), LocalDate.of(2026, 12, 16))));
        when(cycles.findAll())
                .thenReturn(List.of(
                        new EvaluationCycle(10, 1, null, null, null, "CLOSED"),
                        new EvaluationCycle(11, 3, null, null, null, "OPEN")));
    }

    @Test
    void semestersAreNewestFirstWithUndatedSemestersLast() throws Exception {
        mockMvc.perform(get("/api/semesters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].semesterId").value(3))
                .andExpect(jsonPath("$[1].semesterId").value(1))
                .andExpect(jsonPath("$[2].semesterId").value(2));
    }

    @Test
    void signupOpenKeepsOnlySemestersWithAnOpenCycle() throws Exception {
        mockMvc.perform(get("/api/semesters").param("signupOpen", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].semesterId").value(3));
    }

    @Test
    void sectionsPassOnlyTheGivenFiltersThrough() throws Exception {
        when(sections.findFiltered(3, null, 9))
                .thenReturn(List.of(new CourseSection(
                        284, 2, 3, "002", "MW", LocalTime.of(10, 0), LocalTime.of(11, 15), "ECSW 1.355")));

        mockMvc.perform(get("/api/sections").param("semesterId", "3").param("professorId", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sectionId").value(284))
                .andExpect(jsonPath("$[0].startTime").value("10:00:00"));
    }
}
