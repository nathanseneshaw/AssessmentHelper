package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
import org.springframework.jdbc.core.JdbcTemplate;
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
    JdbcTemplate db;

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
                .andExpect(jsonPath("$.criteria[0].title").value("Edited engagement"))
                .andExpect(jsonPath("$.criteria[1].selected").value(false))
                .andExpect(jsonPath("$.criteria[1].notes").value(true));
        assertThat(db.queryForObject(
                        "SELECT COUNT(*) FROM observation_criterion WHERE template_id=?",
                        Integer.class,
                        original.templateId()))
                .isEqualTo(original.criteria().size());
        assertThat(db.queryForObject(
                        "SELECT COUNT(*) FROM observation_template WHERE active_status=TRUE", Integer.class))
                .isEqualTo(1);
        mvc.perform(as(OTHER_AC, put("/api/form-template"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(original.templateId(), CRITERIA)))
                .andExpect(status().isConflict());
        assertThat(service.current().version()).isEqualTo(original.version() + 1);
    }

    @Test
    void rejectsInvalidChangesWithoutModifyingSharedTemplate() throws Exception {
        int original = service.current().templateId();
        for (String criteria : List.of(
                "[]",
                "[{\"title\":\" \" ,\"selected\":true}]",
                "[{\"title\":\"Only one\",\"selected\":false}]",
                "[{\"title\":\"Same\",\"selected\":true},{\"title\":\" same \" ,\"selected\":true}]")) {
            mvc.perform(as(AC, put("/api/form-template"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(original, criteria)))
                    .andExpect(status().isBadRequest());
        }
        assertThat(service.current().templateId()).isEqualTo(original);
    }

    @Test
    void facultyCannotReadOrChangeCommitteeTemplate() throws Exception {
        mvc.perform(as(FACULTY, get("/api/form-template"))).andExpect(status().isForbidden());
        mvc.perform(as(FACULTY, put("/api/form-template"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(service.current().templateId(), CRITERIA)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/form-template")).andExpect(status().isUnauthorized());
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
        var restored = service.current();
        assertThat(restored.criteria()).isEqualTo(original.criteria());
        assertThat(restored.templateId()).isEqualTo(original.templateId());
        assertThat(service.history()).hasSize(2);
        assertThat(service.history().getFirst().criteria()).isEqualTo(changed.criteria());
        mvc.perform(as(AC, get("/api/form-template")))
                .andExpect(jsonPath("$.templateId").value(restored.templateId()));
        mvc.perform(as(OTHER_AC, post("/api/form-template/versions/" + original.templateId() + "/restore"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":" + changed.templateId() + "}"))
                .andExpect(status().isConflict());
        mvc.perform(as(OTHER_AC, post("/api/form-template/versions/99999/restore"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":" + restored.templateId() + "}"))
                .andExpect(status().isNotFound());
        mvc.perform(as(FACULTY, get("/api/form-template/versions"))).andExpect(status().isForbidden());
        mvc.perform(as(FACULTY, post("/api/form-template/versions/" + original.templateId() + "/restore"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":" + restored.templateId() + "}"))
                .andExpect(status().isForbidden());
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
        assertThat(service.history().get(1)).isEqualTo(third);
    }

    @Test
    void seedHasNineUniqueCriteriaWithSevenIncluded() {
        var criteria = service.current().criteria();
        assertThat(criteria).hasSize(9);
        assertThat(criteria.stream().map(FormTemplateService.Criterion::title).toList())
                .doesNotHaveDuplicates();
        assertThat(criteria.stream()
                        .filter(FormTemplateService.Criterion::selected)
                        .toList())
                .hasSize(7);
    }
}
