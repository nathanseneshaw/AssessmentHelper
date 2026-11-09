package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import edu.utdallas.cs4485.assessmenthelper.service.EmailTemplateService;
import edu.utdallas.cs4485.assessmenthelper.service.EvaluationEligibilityService;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(EmailTemplateIntegrationTests.FixedTime.class)
@Transactional
@WithMockUser(roles = "AC")
class EmailTemplateIntegrationTests {
    @TestConfiguration
    static class FixedTime {
        @Bean
        @Primary
        Clock emailClock() {
            return Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneId.of("America/Chicago"));
        }
    }

    @Autowired
    EmailTemplateService templates;

    @Autowired
    EvaluationEligibilityService evaluations;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate db;

    @Test
    void catalogContainsFourDefaultsAndSupportedPlaceholders() throws Exception {
        mvc.perform(get("/api/email-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templates.length()").value(4))
                .andExpect(jsonPath("$.placeholders[0]").value("professorName"));
    }

    @Test
    void previewsUseServerFacultyContextAndDoNotSend() throws Exception {
        int before = db.queryForObject("SELECT COUNT(*) FROM evaluation_notification", Integer.class);
        mvc.perform(get("/api/due-evaluations/1/professors/9/email-preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipient").value("hollis.carrowmore@example.edu"))
                .andExpect(jsonPath("$.subject").value("Faculty Evaluation Due - Fall 2026"));
        var overdue = evaluations.previewNotification(1, 13, false);
        assertThat(overdue.body())
                .contains("Dear Professor Coldwell,", "every year")
                .doesNotContain("{{");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM evaluation_notification", Integer.class))
                .isEqualTo(before);
    }

    @Test
    void recordingEditedEmailUsesFollowUpTemplateNextTime() {
        var draft = evaluations.previewNotification(1, 9, true);
        var saved = evaluations.notifyDueProfessor(
                1, 9, new EvaluationEligibilityService.Email(draft.recipient(), "Edited subject", "Edited body"));
        assertThat(saved.subject()).isEqualTo("Edited subject");
        assertThat(saved.body()).isEqualTo("Edited body");
        var followUp = evaluations.previewNotification(1, 9, true);
        assertThat(followUp.subject()).startsWith("Follow Up:");
        assertThat(followUp.body()).contains("This is a follow-up reminder.");
        var overdue = evaluations.previewNotification(1, 13, false);
        evaluations.notifyProfessor(1, 13, overdue);
        assertThat(evaluations.previewNotification(1, 13, false).body())
                .contains("follow-up reminder about your overdue");
    }

    @Test
    void savedTemplateIsUsedByExistingNotificationFlow() {
        var current = templates.catalog().templates().stream()
                .filter(t -> t.templateType().equals("EVALUATION_DUE"))
                .findFirst()
                .orElseThrow();
        templates.save(
                "EVALUATION_DUE",
                new EmailTemplateService.Update(
                        "Due",
                        "Hello {{lastName}}",
                        "Register by {{signupDeadline}} for {{cycleLabel}}.",
                        true,
                        current.version()));
        var draft = evaluations.previewNotification(1, 9, true);
        assertThat(draft.subject()).isEqualTo("Hello Carrowmore");
        assertThat(draft.body()).isEqualTo("Register by 2026-10-31 for Fall 2026.");
    }

    @Test
    void updatesRejectStaleVersions() {
        var current = templates.catalog().templates().getFirst();
        var update = new EmailTemplateService.Update(
                current.name(), current.subject(), current.body(), true, current.version());
        templates.save(current.templateType(), update);
        assertThatThrownBy(() -> templates.save(current.templateType(), update)).hasMessageContaining("changed");
    }

    @Test
    void unknownOrMalformedVariablesAndSubjectNewlinesAreRejected() {
        for (String text : new String[] {"{{typo}}", "{{lastName}", "{{ lastName }}"})
            assertThatThrownBy(() -> templates.save(
                            "CUSTOM", new EmailTemplateService.Update("Custom", "Subject", text, true, null)))
                    .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> templates.preview(
                        new EmailTemplateService.Update("X", "Hello\nBcc: other@example.edu", "Body", true, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newScenarioTemplatesCanBeCreatedAndRenderedWithoutChangingRenderer() {
        var created = templates.save(
                "OBSERVATION_REMINDER",
                new EmailTemplateService.Update(
                        "Reminder",
                        "Reminder for {{lastName}}",
                        "Please submit {{courseName}} by {{observationDeadline}}.",
                        true,
                        null));
        assertThat(created.templateId()).isNotNull();
        var preview = templates.render(
                "OBSERVATION_REMINDER",
                Map.of("lastName", "Nguyen", "courseName", "CS 3345", "observationDeadline", "2026-11-15"));
        assertThat(preview.body()).isEqualTo("Please submit CS 3345 by 2026-11-15.");
    }

    @Test
    void inactiveTemplatesAndMissingContextPreventPreparingEmail() {
        var current = templates.catalog().templates().stream()
                .filter(t -> t.templateType().equals("EVALUATION_DUE"))
                .findFirst()
                .orElseThrow();
        templates.save(
                "EVALUATION_DUE",
                new EmailTemplateService.Update(
                        current.name(), current.subject(), current.body(), false, current.version()));
        assertThatThrownBy(() -> evaluations.previewNotification(1, 9, true)).hasMessageContaining("inactive");
        assertThatThrownBy(() -> templates.render("EVALUATION_OVERDUE", Map.of()))
                .hasMessageContaining("Missing email information");
    }

    @Test
    void substitutionsAreLiteralAndNeverRecursivelyRendered() {
        templates.save(
                "LITERAL",
                new EmailTemplateService.Update("Literal", "Hello {{lastName}}", "{{professorName}}", true, null));
        var result = templates.render("LITERAL", Map.of("lastName", "Dollar $5", "professorName", "{{cycleLabel}}"));
        assertThat(result.subject()).isEqualTo("Hello Dollar $5");
        assertThat(result.body()).isEqualTo("{{cycleLabel}}");
    }

    @Test
    void previewRejectsWrongCycleAndWrongEligibility() throws Exception {
        mvc.perform(get("/api/due-evaluations/99/professors/9/email-preview")).andExpect(status().isConflict());
        mvc.perform(get("/api/due-evaluations/1/professors/13/email-preview")).andExpect(status().isConflict());
    }

    @Test
    void templateEditingRequiresCsrf() throws Exception {
        mvc.perform(put("/api/email-templates/CUSTOM")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Custom\",\"subject\":\"Hi\",\"body\":\"Body\",\"activeStatus\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/email-templates/CUSTOM")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Custom\",\"subject\":\"Hi\",\"body\":\"Body\",\"activeStatus\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PROFESSOR")
    void facultyCannotReadEditOrPreviewTemplates() throws Exception {
        mvc.perform(get("/api/email-templates")).andExpect(status().isForbidden());
        mvc.perform(post("/api/email-templates/preview")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/email-templates/CUSTOM")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/due-evaluations/1/professors/9/email-preview")).andExpect(status().isForbidden());
    }
}
