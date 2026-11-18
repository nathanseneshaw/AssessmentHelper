package edu.utdallas.cs4485.assessmenthelper.controller;

import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.observationOf;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.principal;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.professor;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ObservationReadIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcAggregateTemplate db;

    // From data.sql: Keira observes Leander's CS 3345.001 on 2026-11-04 and has not started yet. In Fall 2025 Arlo
    // observed Keira and that form is complete. Dario has no part in any of these.
    private AccountPrincipal keira;
    private AccountPrincipal kaia;
    private AccountPrincipal dario;
    private int scheduled;
    private int keirasOwn;

    @BeforeEach
    void demoObservations() {
        keira = principal(db, "keira.larkspur@example.edu", 1, Role.PROFESSOR);
        kaia = principal(db, "kaia.clearbrook@example.edu", 1, Role.AC, Role.PROFESSOR);
        dario = principal(db, "dario.hollowell@example.edu", 1, Role.PROFESSOR);
        scheduled = observationOf(db, "leander.coldwell@example.edu", "Fall", 2026);
        keirasOwn = observationOf(db, "keira.larkspur@example.edu", "Fall", 2025);
    }

    @Test
    void committeeCanReadAbelsEvaluationHistoryAcrossSchools() throws Exception {
        Integer abelId = professor(db, "abel.blythewood@example.edu").professorId();
        String path = "/api/professors/" + abelId + "/evaluations";
        read(kaia, path)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cycleId").isNumber());
        read(dario, path).andExpect(status().isForbidden());
    }

    @Test
    void observerSeesTheScheduledFormAndMayStartIt() throws Exception {
        read(keira, "/api/observations/" + scheduled)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.viewerRole").value("OBSERVER"))
                .andExpect(jsonPath("$.observee.name").value("Leander Coldwell"))
                .andExpect(jsonPath("$.course.label").value("CS 3345.001"))
                .andExpect(jsonPath("$.observationDate").value("2026-11-04"))
                .andExpect(jsonPath("$.template.criteria.length()").value(7))
                .andExpect(jsonPath("$.canStart").value(true))
                .andExpect(jsonPath("$.canEdit").value(false));
    }

    @Test
    void outsidersAreForbiddenAndUnknownIdsAreNotFound() throws Exception {
        read(dario, "/api/observations/" + scheduled).andExpect(status().isForbidden());
        read(dario, "/api/professors/" + keira.user().professorId() + "/observations")
                .andExpect(status().isForbidden());
        read(keira, "/api/observations/99999").andExpect(status().isNotFound());
    }

    @Test
    void professorListShowsBothSidesNewestFirst() throws Exception {
        read(keira, "/api/professors/" + keira.user().professorId() + "/observations")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].observationId").value(contains(scheduled, keirasOwn)))
                .andExpect(jsonPath("$[*].role").value(contains("OBSERVER", "OBSERVEE")))
                .andExpect(jsonPath("$[0].observeeName").value("Leander Coldwell"))
                .andExpect(jsonPath("$[1].observerName").value("Arlo Glimmerton"));
    }

    private ResultActions read(AccountPrincipal principal, String path) throws Exception {
        return mvc.perform(get(path).with(user(principal)));
    }
}
