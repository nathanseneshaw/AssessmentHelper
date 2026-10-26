package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.config.SecurityConfig;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverRequest;
import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.ProfessorAccess;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import edu.utdallas.cs4485.assessmenthelper.service.ConflictException;
import edu.utdallas.cs4485.assessmenthelper.service.NotFoundException;
import edu.utdallas.cs4485.assessmenthelper.service.ObserverRequestService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ObserverRequestController.class)
@Import(SecurityConfig.class)
@WithMockUser
class ObserverRequestControllerTests {

    private static final ObserverRequest PENDING = new ObserverRequest(
            1, 2, 3, LocalDateTime.of(2026, 10, 20, 12, 0), LocalDateTime.of(2026, 10, 27, 12, 0), null, "PENDING");

    private static final AccountPrincipal OBSERVER = new AccountPrincipal(
            new CurrentUser(3, 3, 1, "Test Observer", "observer@univ.edu", List.of(Role.PROFESSOR)), null, true);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ObserverRequestService service;

    @MockitoBean(name = "professorAccess")
    private ProfessorAccess professorAccess;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void createReturns201WithPendingRequest() throws Exception {
        when(service.create(2, 3, null)).thenReturn(PENDING);

        mockMvc.perform(post("/api/observer-requests")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signupId\":2,\"observerId\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createWithBadInputReturns400() throws Exception {
        when(service.create(null, 3, null))
                .thenThrow(new IllegalArgumentException("signupId and observerId are required"));

        mockMvc.perform(post("/api/observer-requests")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observerId\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("signupId and observerId are required"));
    }

    @Test
    void getUnknownRequestReturns404() throws Exception {
        when(service.findById(9)).thenThrow(new NotFoundException("No observer request with id 9"));

        mockMvc.perform(get("/api/observer-requests/9")).andExpect(status().isNotFound());
    }

    @Test
    void confirmingExpiredRequestReturns409() throws Exception {
        when(service.confirm(1)).thenThrow(new ConflictException("Request 1 has expired"));

        mockMvc.perform(post("/api/observer-requests/1/confirm").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Request 1 has expired"));
    }

    @Test
    void declineReturnsDeclinedRequest() throws Exception {
        ObserverRequest declined = new ObserverRequest(
                1,
                2,
                3,
                LocalDateTime.of(2026, 10, 20, 12, 0),
                LocalDateTime.of(2026, 10, 27, 12, 0),
                LocalDateTime.of(2026, 10, 21, 9, 0),
                "DECLINED");
        when(professorAccess.isRequestedObserver(any(), eq(1))).thenReturn(true);
        when(service.decline(1)).thenReturn(declined);

        mockMvc.perform(post("/api/observer-requests/1/decline")
                        .with(user(OBSERVER))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));
    }

    @Test
    void decliningExpiredRequestReturns409() throws Exception {
        when(professorAccess.isRequestedObserver(any(), eq(1))).thenReturn(true);
        when(service.decline(1)).thenThrow(new ConflictException("Request 1 has expired"));

        mockMvc.perform(post("/api/observer-requests/1/decline")
                        .with(user(OBSERVER))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Request 1 has expired"));
    }

    @Test
    void decliningARequestSentToSomeoneElseReturns403() throws Exception {
        when(professorAccess.isRequestedObserver(any(), eq(1))).thenReturn(false);

        mockMvc.perform(post("/api/observer-requests/1/decline")
                        .with(user(OBSERVER))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You do not have access to this resource."));

        verify(service, never()).decline(anyInt());
    }
}
