package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.config.SecurityConfig;
import edu.utdallas.cs4485.assessmenthelper.entity.CourseSection;
import edu.utdallas.cs4485.assessmenthelper.service.ObserverAvailabilityService;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ObserverAvailabilityController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "AC")
class ObserverAvailabilityControllerTests {

    private static final CourseSection CLASH =
            new CourseSection(5, 1, 1, "001", "MW", LocalTime.of(10, 30), LocalTime.of(11, 45), null);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ObserverAvailabilityService service;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @WithMockUser(roles = "PROFESSOR")
    void professorsCannotCheckAvailability() throws Exception {
        mockMvc.perform(get("/api/observer-availability").param("signupId", "2").param("observerId", "3"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void unavailableObserverListsTheClashingSection() throws Exception {
        when(service.conflicts(3, 2)).thenReturn(List.of(CLASH));

        mockMvc.perform(get("/api/observer-availability").param("signupId", "2").param("observerId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.conflicts[0].sectionId").value(5));
    }

    @Test
    void missingParameterReturns400() throws Exception {
        mockMvc.perform(get("/api/observer-availability").param("observerId", "3"))
                .andExpect(status().isBadRequest());
    }
}
