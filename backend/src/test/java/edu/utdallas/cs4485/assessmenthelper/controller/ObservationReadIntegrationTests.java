package edu.utdallas.cs4485.assessmenthelper.controller;

import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.cycleOf;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.inactive;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.observationOf;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.only;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.principal;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.submitted;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.withPlan;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.withStatus;
import static java.util.Comparator.comparing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.entity.Observation;
import edu.utdallas.cs4485.assessmenthelper.entity.ObservationCriterion;
import edu.utdallas.cs4485.assessmenthelper.entity.ObservationResponse;
import edu.utdallas.cs4485.assessmenthelper.entity.Observee;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationSignupRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationCriterionRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationResponseRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationTemplateRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserveeRepository;
import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ObservationReadIntegrationTests.FixedTime.class)
@Transactional
class ObservationReadIntegrationTests {

    @TestConfiguration
    static class FixedTime {
        @Bean
        @Primary
        Clock observationClock() {
            return Clock.fixed(Instant.parse("2026-10-27T15:00:00Z"), ZoneId.of("America/Chicago"));
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcAggregateTemplate db;

    @Autowired
    private ObservationRepository observations;

    @Autowired
    private EvaluationSignupRepository signups;

    @Autowired
    private ObserveeRepository observees;

    @Autowired
    private ObservationTemplateRepository templates;

    @Autowired
    private ObservationCriterionRepository criteria;

    @Autowired
    private ObservationResponseRepository responses;

    // From data.sql: Keira observes Leander's CS 3345.001 on 2026-11-04 and has not started yet. In Fall 2025 Arlo
    // observed both Keira and Luca, and both forms are complete. Kaia is on the Assessment Committee in their school;
    // Dario has no part in any of these.
    private AccountPrincipal keira;
    private AccountPrincipal leander;
    private AccountPrincipal arlo;
    private AccountPrincipal luca;
    private AccountPrincipal kaia;
    private AccountPrincipal dario;
    private int scheduled;
    private int completed;
    private int keirasOwn;

    @BeforeEach
    void demoObservations() {
        keira = principal(db, "keira.larkspur@example.edu", 1, Role.PROFESSOR);
        leander = principal(db, "leander.coldwell@example.edu", 1, Role.PROFESSOR);
        arlo = principal(db, "arlo.glimmerton@example.edu", 1, Role.PROFESSOR);
        luca = principal(db, "luca.lindenhall@example.edu", 1, Role.PROFESSOR);
        kaia = principal(db, "kaia.clearbrook@example.edu", 1, Role.AC, Role.PROFESSOR);
        dario = principal(db, "dario.hollowell@example.edu", 1, Role.PROFESSOR);
        scheduled = observationOf(db, "leander.coldwell@example.edu", "Fall", 2026);
        completed = observationOf(db, "luca.lindenhall@example.edu", "Fall", 2025);
        keirasOwn = observationOf(db, "keira.larkspur@example.edu", "Fall", 2025);
    }

    @Test
    void observerSeesTheScheduledFormAndMayStartIt() throws Exception {
        read(keira, "/api/observations/" + scheduled)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observationId").value(scheduled))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.viewerRole").value("OBSERVER"))
                .andExpect(
                        jsonPath("$.observee.professorId").value(leander.user().professorId()))
                .andExpect(jsonPath("$.observee.name").value("Leander Coldwell"))
                .andExpect(jsonPath("$.observee.department").value("Computer Science"))
                .andExpect(jsonPath("$.observer.professorId").value(keira.user().professorId()))
                .andExpect(jsonPath("$.observer.name").value("Keira Larkspur"))
                .andExpect(jsonPath("$.course.label").value("CS 3345.001"))
                .andExpect(jsonPath("$.course.courseName")
                        .value("Data Structures and Foundations of Algorithmic Analysis"))
                .andExpect(jsonPath("$.course.meetingDays").value("MW"))
                .andExpect(jsonPath("$.course.startTime").value("08:30:00"))
                .andExpect(jsonPath("$.course.endTime").value("09:45:00"))
                .andExpect(jsonPath("$.semesterLabel").value("Fall 2026"))
                .andExpect(jsonPath("$.cycleId").value(cycleOf(db, "Fall", 2026)))
                .andExpect(jsonPath("$.observationDate").value("2026-11-04"))
                .andExpect(jsonPath("$.startTime").value("08:30:00"))
                .andExpect(jsonPath("$.template.criteria.length()").value(7))
                .andExpect(jsonPath("$.responses.length()").value(0))
                .andExpect(jsonPath("$.submittedAt").value(nullValue()))
                .andExpect(jsonPath("$.canStart").value(true))
                .andExpect(jsonPath("$.canEdit").value(false))
                // The raw signature flags are not sent; only what the viewer may do next and the observee's part.
                .andExpect(jsonPath("$.observerSigned").doesNotExist())
                .andExpect(jsonPath("$.observeeSigned").doesNotExist())
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andExpect(jsonPath("$.canSign").value(false))
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.observeeComments").value(nullValue()))
                .andExpect(jsonPath("$.observeeSignedAt").value(nullValue()));
    }

    @Test
    void formListsTheSevenQuestionsInOrder() throws Exception {
        int templateId = only(templates.findAll(), t -> t.templateName().equals("Standard Classroom Observation"))
                .templateId();
        read(keira, "/api/observations/" + scheduled)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.template.templateId").value(templateId))
                .andExpect(jsonPath("$.template.templateName").value("Standard Classroom Observation"))
                .andExpect(jsonPath("$.template.version").value(1))
                .andExpect(jsonPath("$.template.criteria[*].criterionText")
                        .value(contains(
                                "Student Engagement",
                                "Preparedness & Organization",
                                "Content Presentation",
                                "Rigor & Depth",
                                "Pace & Time for Questions",
                                "Strengths observed",
                                "Suggestions for improvement")))
                .andExpect(jsonPath("$.template.criteria[*].questionType")
                        .value(contains("RATING", "RATING", "RATING", "RATING", "RATING", "TEXT", "TEXT")))
                .andExpect(jsonPath("$.template.criteria[*].required")
                        .value(contains(true, true, true, true, true, true, false)))
                .andExpect(jsonPath("$.template.criteria[*].displayOrder").value(contains(1, 2, 3, 4, 5, 6, 7)))
                .andExpect(jsonPath("$.template.criteria[0].ratingMin").value(1))
                .andExpect(jsonPath("$.template.criteria[0].ratingMax").value(5))
                .andExpect(jsonPath("$.template.criteria[5].ratingMin").value(nullValue()))
                .andExpect(jsonPath("$.template.criteria[5].ratingMax").value(nullValue()));

        // Questions follow display order, not insertion order, and the form keeps its template once that is retired.
        criteria.saveAll(criteria.findAll().stream()
                .filter(c -> c.criterionText().equals("Student Engagement"))
                .map(c -> new ObservationCriterion(
                        c.criterionId(),
                        c.templateId(),
                        c.criterionText(),
                        c.questionType(),
                        c.required(),
                        8,
                        c.ratingMin(),
                        c.ratingMax(),
                        c.selected(),
                        c.notesEnabled()))
                .toList());
        templates.findAll().forEach(t -> templates.save(inactive(t)));
        read(keira, "/api/observations/" + scheduled)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.template.templateId").value(templateId))
                .andExpect(jsonPath("$.template.criteria[0].criterionText").value("Preparedness & Organization"))
                .andExpect(jsonPath("$.template.criteria[6].criterionText").value("Student Engagement"));
    }

    @Test
    void observeeSeesTheFormOnlyOnceTheObserverSubmits() throws Exception {
        read(leander, "/api/observations/" + scheduled).andExpect(status().isForbidden());

        // A started draft is still the observer's alone.
        updateObservation(scheduled, o -> withStatus(o, "IN_PROGRESS"));
        responses.save(new ObservationResponse(null, scheduled, criterion("Student Engagement"), 4, "Draft"));
        read(leander, "/api/observations/" + scheduled).andExpect(status().isForbidden());
        read(keira, "/api/observations/" + scheduled)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submittedAt").value(nullValue()))
                .andExpect(jsonPath("$.canStart").value(false))
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.responses[0].rating").value(4));

        // Submitted, both may read it and neither may change it.
        updateObservation(scheduled, o -> submitted(o, LocalDateTime.of(2026, 11, 4, 10, 0)));
        read(leander, "/api/observations/" + scheduled)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerRole").value("OBSERVEE"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submittedAt").value("2026-11-04T10:00:00"))
                .andExpect(jsonPath("$.responses[0].criterionId").value(criterion("Student Engagement")))
                .andExpect(jsonPath("$.responses[0].comments").value("Draft"))
                .andExpect(jsonPath("$.canStart").value(false))
                .andExpect(jsonPath("$.canEdit").value(false));
        read(keira, "/api/observations/" + scheduled)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerRole").value("OBSERVER"))
                .andExpect(jsonPath("$.submittedAt").value("2026-11-04T10:00:00"))
                .andExpect(jsonPath("$.canStart").value(false))
                .andExpect(jsonPath("$.canEdit").value(false));
    }

    @Test
    void bothSidesReopenACompletedFormWithItsAnswers() throws Exception {
        // data.sql records no answers on the completed forms, so give this one a full set.
        responses.saveAll(criteria
                .findByTemplateIdOrderByDisplayOrderAscCriterionIdAsc(
                        observations.findById(completed).orElseThrow().templateId())
                .stream()
                .filter(ObservationCriterion::selected)
                .map(q -> new ObservationResponse(
                        null,
                        completed,
                        q.criterionId(),
                        "RATING".equals(q.questionType()) ? 4 : null,
                        "Notes on " + q.criterionText()))
                .toList());
        List<ObservationResponse> stored = responses.findByObservationId(completed).stream()
                .sorted(comparing(
                        r -> criteria.findById(r.criterionId()).orElseThrow().displayOrder()))
                .toList();
        assertThat(stored).hasSize(7);
        read(arlo, "/api/observations/" + completed)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerRole").value("OBSERVER"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.observee.name").value("Luca Lindenhall"))
                .andExpect(jsonPath("$.observer.name").value("Arlo Glimmerton"))
                .andExpect(jsonPath("$.semesterLabel").value("Fall 2025"))
                .andExpect(jsonPath("$.submittedAt").value("2025-10-07T15:00:00"))
                .andExpect(jsonPath("$.responses[*].criterionId")
                        .value(contains(column(stored, ObservationResponse::criterionId))))
                .andExpect(
                        jsonPath("$.responses[*].rating").value(contains(column(stored, ObservationResponse::rating))))
                .andExpect(jsonPath("$.responses[*].comments")
                        .value(contains(column(stored, ObservationResponse::comments))))
                .andExpect(jsonPath("$.canStart").value(false))
                .andExpect(jsonPath("$.canEdit").value(false));
        read(luca, "/api/observations/" + completed)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerRole").value("OBSERVEE"))
                .andExpect(jsonPath("$.responses.length()").value(7))
                .andExpect(jsonPath("$.canStart").value(false))
                .andExpect(jsonPath("$.canEdit").value(false));
    }

    @Test
    void theCommitteeReadsOnlyCompletedFormsInItsOwnSchool() throws Exception {
        for (String status : List.of("ACCEPTED", "SCHEDULED", "IN_PROGRESS", "SUBMITTED")) {
            updateObservation(scheduled, o -> withStatus(o, status));
            read(kaia, "/api/observations/" + scheduled).andExpect(status().isForbidden());
            read(keira, "/api/observations/" + scheduled).andExpect(status().isOk());
        }
        updateObservation(scheduled, o -> withStatus(o, "COMPLETED"));
        read(kaia, "/api/observations/" + scheduled)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerRole").value("COMMITTEE"))
                .andExpect(jsonPath("$.locked").value(true))
                .andExpect(jsonPath("$.canStart").value(false))
                .andExpect(jsonPath("$.canEdit").value(false))
                .andExpect(jsonPath("$.canSign").value(false));
        read(kaia, "/api/observations/" + completed).andExpect(status().isOk());
        read(kaia, "/api/observations/" + keirasOwn).andExpect(status().isOk());

        // A committee member from another school has no access, and neither does a professor without the role.
        AccountPrincipal otherSchool = principal(db, "kaia.clearbrook@example.edu", 2, Role.AC, Role.PROFESSOR);
        read(otherSchool, "/api/observations/" + completed).andExpect(status().isForbidden());
        read(dario, "/api/observations/" + completed).andExpect(status().isForbidden());
    }

    @Test
    void aCommitteeMemberWhoIsTheObserveeSeesTheirOwnFormAsTheObservee() throws Exception {
        // Kaia's own class is observed, so being on the committee must not show her the draft.
        Observee observee = observees
                .findById(signups.findById(
                                observations.findById(scheduled).orElseThrow().signupId())
                        .orElseThrow()
                        .observeeId())
                .orElseThrow();
        observees.save(
                new Observee(observee.observeeId(), kaia.user().professorId(), observee.cycleId(), observee.status()));
        updateObservation(scheduled, o -> withStatus(o, "IN_PROGRESS"));
        read(kaia, "/api/observations/" + scheduled).andExpect(status().isForbidden());

        updateObservation(scheduled, o -> submitted(o, LocalDateTime.of(2026, 11, 4, 10, 0)));
        read(kaia, "/api/observations/" + scheduled)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerRole").value("OBSERVEE"))
                .andExpect(jsonPath("$.observee.name").value("Kaia Clearbrook"))
                .andExpect(jsonPath("$.canEdit").value(false));
    }

    @Test
    void outsidersAreForbiddenAndUnknownIdsAreNotFound() throws Exception {
        read(dario, "/api/observations/" + scheduled).andExpect(status().isForbidden());
        read(dario, "/api/observations/" + completed).andExpect(status().isForbidden());
        read(dario, "/api/professors/" + keira.user().professorId() + "/observations")
                .andExpect(status().isForbidden());
        read(keira, "/api/observations/99999").andExpect(status().isNotFound());
        read(kaia, "/api/observations/99999").andExpect(status().isNotFound());
    }

    @Test
    void theCommitteeListAndTemplateEndpointsAreGone() throws Exception {
        read(kaia, "/api/observations").andExpect(status().isNotFound());
        read(kaia, "/api/observations?cycleId=" + cycleOf(db, "Fall", 2025)).andExpect(status().isNotFound());
        read(dario, "/api/observation-templates/active").andExpect(status().isNotFound());
    }

    @Test
    void professorListShowsBothSidesNewestFirst() throws Exception {
        String path = "/api/professors/" + keira.user().professorId() + "/observations";
        read(keira, path)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].observationId").value(contains(scheduled, keirasOwn)))
                .andExpect(jsonPath("$[0].role").value("OBSERVER"))
                .andExpect(jsonPath("$[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$[0].observeeName").value("Leander Coldwell"))
                .andExpect(jsonPath("$[0].observerName").value("Keira Larkspur"))
                .andExpect(jsonPath("$[0].courseLabel").value("CS 3345.001"))
                .andExpect(jsonPath("$[0].semesterLabel").value("Fall 2026"))
                .andExpect(jsonPath("$[0].cycleId").value(cycleOf(db, "Fall", 2026)))
                .andExpect(jsonPath("$[0].observationDate").value("2026-11-04"))
                .andExpect(jsonPath("$[0].startTime").value("08:30:00"))
                .andExpect(jsonPath("$[0].submittedAt").value(nullValue()))
                .andExpect(jsonPath("$[1].role").value("OBSERVEE"))
                .andExpect(jsonPath("$[1].status").value("COMPLETED"))
                .andExpect(jsonPath("$[1].observerName").value("Arlo Glimmerton"))
                .andExpect(jsonPath("$[1].semesterLabel").value("Fall 2025"))
                .andExpect(jsonPath("$[1].cycleId").value(cycleOf(db, "Fall", 2025)))
                .andExpect(jsonPath("$[1].submittedAt").value("2025-10-28T15:00:00"))
                .andExpect(jsonPath("$[1].observeeSignedAt").doesNotExist())
                .andExpect(jsonPath("$[1].completedAt").doesNotExist());
        // The committee reads the same list from Keira's point of view.
        read(kaia, path)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].role").value(contains("OBSERVER", "OBSERVEE")));
        // Leander's observation is listed for him before he can open it.
        read(leander, "/api/professors/" + leander.user().professorId() + "/observations")
                .andExpect(jsonPath("$[*].observationId").value(contains(scheduled)))
                .andExpect(jsonPath("$[0].role").value("OBSERVEE"));

        // Arlo observed Luca on 2025-10-07 and Keira on 2025-10-28, so Keira's comes first.
        String arlos = "/api/professors/" + arlo.user().professorId() + "/observations";
        read(arlo, arlos)
                .andExpect(jsonPath("$[*].observationId").value(containsInRelativeOrder(keirasOwn, completed)));

        // An observation without a date yet, as when one has just been accepted, comes first.
        updateObservation(completed, o -> withPlan(o, null, o.startTime()));
        read(arlo, arlos).andExpect(jsonPath("$[0].observationId").value(completed));
    }

    private ResultActions read(AccountPrincipal principal, String path) throws Exception {
        return mvc.perform(get(path).with(user(principal)));
    }

    private int criterion(String text) {
        return only(criteria.findAll(), c -> c.criterionText().equals(text)).criterionId();
    }

    private void updateObservation(int observationId, UnaryOperator<Observation> change) {
        observations.save(change.apply(observations.findById(observationId).orElseThrow()));
    }

    private static Object[] column(List<ObservationResponse> rows, Function<ObservationResponse, Object> value) {
        return rows.stream().map(value).toArray();
    }
}
