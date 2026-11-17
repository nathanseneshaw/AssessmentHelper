package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationNotificationRepository;
import edu.utdallas.cs4485.assessmenthelper.service.EmailTemplateService;
import edu.utdallas.cs4485.assessmenthelper.service.EvaluationEligibilityService;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
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
    EvaluationNotificationRepository notifications;

    @Test
    void catalogHasFourDefaultsAndPlaceholdersWhenNoTemplateIsEdited() throws Exception {
        mvc.perform(get("/api/email-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templates.length()").value(4))
                .andExpect(jsonPath("$.placeholders[0]").value("professorName"));
    }

    @Test
    void previewFillsInProfessorDetailsWithoutSendingWhenProfessorIsDue() throws Exception {
        long before = notifications.count();
        mvc.perform(get("/api/due-evaluations/1/professors/9/email-preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipient").value("hollis.carrowmore@example.edu"))
                .andExpect(jsonPath("$.subject").value("Faculty Evaluation Due - Fall 2026"));
        assertThat(notifications.count()).isEqualTo(before);
    }

    @Test
    void previewReturns409WhenProfessorIsNotDue() throws Exception {
        // Leander (13) is overdue rather than due.
        mvc.perform(get("/api/due-evaluations/1/professors/13/email-preview")).andExpect(status().isConflict());
    }

    @Test
    void followUpTemplateIsUsedWhenEmailWasAlreadySent() {
        var draft = evaluations.previewNotification(1, 9, true);
        var saved = evaluations.notifyDueProfessor(
                1, 9, new EvaluationEligibilityService.Email(draft.recipient(), "Edited subject", "Edited body"));
        assertThat(saved.subject()).isEqualTo("Edited subject");
        assertThat(saved.body()).isEqualTo("Edited body");
        var followUp = evaluations.previewNotification(1, 9, true);
        assertThat(followUp.subject()).startsWith("Follow Up:");
    }

    @Test
    void notificationUsesSavedTemplateWhenTemplateIsEdited() throws Exception {
        var current = templates.catalog().templates().stream()
                .filter(t -> t.templateType().equals("EVALUATION_DUE"))
                .findFirst()
                .orElseThrow();
        mvc.perform(put("/api/email-templates/EVALUATION_DUE")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Due","subject":"Hello {{lastName}}",
                                 "body":"Register by {{signupDeadline}} for {{cycleLabel}}.","activeStatus":true,
                                 "version":%d}
                                """.formatted(current.version())))
                .andExpect(status().isOk());
        var draft = evaluations.previewNotification(1, 9, true);
        assertThat(draft.subject()).isEqualTo("Hello Carrowmore");
        assertThat(draft.body()).isEqualTo("Register by 2026-10-31 for Fall 2026.");
    }

    @Test
    void saveIsRejectedWhenTemplateVersionIsStale() {
        var current = templates.catalog().templates().getFirst();
        var update = new EmailTemplateService.Update(
                current.name(), current.subject(), current.body(), true, current.version());
        templates.save(current.templateType(), update);
        assertThatThrownBy(() -> templates.save(current.templateType(), update)).hasMessageContaining("changed");
    }

    @Test
    void saveIsRejectedWhenTemplateHasUnknownPlaceholder() {
        assertThatThrownBy(() -> templates.save(
                        "CUSTOM", new EmailTemplateService.Update("Custom", "Subject", "{{typo}}", true, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
