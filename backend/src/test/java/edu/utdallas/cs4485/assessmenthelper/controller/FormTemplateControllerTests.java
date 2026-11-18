package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import edu.utdallas.cs4485.assessmenthelper.repository.ObservationCriterionRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationTemplateRepository;
import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import edu.utdallas.cs4485.assessmenthelper.service.FormTemplateService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FormTemplateControllerTests {
    @Autowired
    MockMvc mvc;

    @Autowired
    ObservationTemplateRepository templates;

    @Autowired
    ObservationCriterionRepository criteria;

    @Autowired
    FormTemplateService service;

    private static final AccountPrincipal AC = principal(1, Role.AC);
    private static final AccountPrincipal OTHER_AC = principal(3, Role.AC);
    private static final AccountPrincipal FACULTY = principal(2, Role.PROFESSOR);

    private static AccountPrincipal principal(int userId, Role role) {
        return new AccountPrincipal(
                new CurrentUser(userId, userId, 1, "Test", "test@example.edu", List.of(role)), null, true);
    }

    private MockHttpServletRequestBuilder as(AccountPrincipal principal, MockHttpServletRequestBuilder request)
            throws Exception {
        Cookie token =
                mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request.with(user(principal)).cookie(token).header("X-XSRF-TOKEN", token.getValue());
    }

    private String body(int templateId, String criteria) {
        return "{\"templateId\":" + templateId + ",\"criteria\":" + criteria + "}";
    }

    private static final String CRITERIA = """
            [{"title":"Edited engagement","selected":true,"notes":false},
             {"title":"New criterion","selected":false,"notes":true}]
            """;

    @Test
    void savesSharedCriteriaAndPreservesOldTemplate() throws Exception {
        var original = service.current();
        mvc.perform(as(AC, put("/api/form-template"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(original.templateId(), CRITERIA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(original.version() + 1))
                .andExpect(jsonPath("$.criteria.length()").value(2));
        mvc.perform(as(OTHER_AC, get("/api/form-template")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criteria[0].title").value("Edited engagement"));
        assertThat(criteria.findByTemplateIdOrderByDisplayOrderAscCriterionIdAsc(original.templateId()))
                .hasSize(original.criteria().size());
        // A committee member still editing the old version is told someone else saved first.
        mvc.perform(as(OTHER_AC, put("/api/form-template"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(original.templateId(), CRITERIA)))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidChangesWithoutModifyingSharedTemplate() throws Exception {
        int original = service.current().templateId();
        mvc.perform(as(AC, put("/api/form-template"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(original, "[{\"title\":\"Only one\",\"selected\":false,\"notes\":false}]")))
                .andExpect(status().isBadRequest());
        assertThat(service.current().templateId()).isEqualTo(original);
    }

    @Test
    void facultyCannotUseTheCommitteeTemplateEditor() throws Exception {
        mvc.perform(as(FACULTY, get("/api/form-template"))).andExpect(status().isForbidden());
    }

    @Test
    void reactivatesPreviousVersionAndPreservesNewerHistory() throws Exception {
        var original = service.current();
        var changed = service.save(
                new FormTemplateService.Update(
                        original.templateId(), List.of(new FormTemplateService.Criterion("Changed", true, false))),
                1);
        mvc.perform(as(OTHER_AC, get("/api/form-template/versions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].templateId").value(changed.templateId()))
                .andExpect(jsonPath("$[1].templateId").value(original.templateId()));
        mvc.perform(as(OTHER_AC, post("/api/form-template/versions/" + original.templateId() + "/restore"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":" + changed.templateId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(original.version()));
        assertThat(service.current()).isEqualTo(original);
        assertThat(service.history()).hasSize(2);
        assertThat(service.history().getFirst().criteria()).isEqualTo(changed.criteria());
    }

    @Test
    void editingAfterRevertUsesNextUnusedVersionNumber() {
        var first = service.current();
        var second = service.save(
                new FormTemplateService.Update(
                        first.templateId(), List.of(new FormTemplateService.Criterion("Second", true, true))),
                1);
        var third = service.save(
                new FormTemplateService.Update(
                        second.templateId(), List.of(new FormTemplateService.Criterion("Third", true, false))),
                1);
        assertThat(service.restore(first.templateId(), third.templateId())).isEqualTo(first);
        var fourth = service.save(
                new FormTemplateService.Update(
                        first.templateId(), List.of(new FormTemplateService.Criterion("Fourth", true, false))),
                1);
        assertThat(fourth.version()).isEqualTo(third.version() + 1);
        assertThat(service.history()).hasSize(4);
    }
}
