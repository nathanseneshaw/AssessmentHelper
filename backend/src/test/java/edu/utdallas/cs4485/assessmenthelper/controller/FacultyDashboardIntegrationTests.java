package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs on data.sql's Fall 2026 demo (evaluation cycle 1, signups close 2026-10-31, feedback is due 2026-12-01).
 * Leander (13) is overdue and Keira (41) observes him on 2026-11-04. Keira is due and signed up with no observer yet.
 * Hollis (9) and Dario (34) are due and have not signed up. Kaia (12) is not due until Spring 2027.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AcDashboardIntegrationTests.Time.class)
@Transactional
class FacultyDashboardIntegrationTests {
    private static final String TODAY = "2026-10-26T15:00:00Z";
    private static final int HOLLIS = 9, KAIA = 12, LEANDER = 13, DARIO = 34, KEIRA = 41;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private AcDashboardIntegrationTests.MovableClock clock;

    @BeforeEach
    void resetClock() {
        clock.set(TODAY);
    }

    @Test
    void anObserveeSeesTheirScheduledObservation() throws Exception {
        dashboard(LEANDER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycleId").value(1))
                .andExpect(jsonPath("$.cycleLabel").value("Fall 2026"))
                .andExpect(jsonPath("$.profile.name").value("Leander Coldwell"))
                .andExpect(jsonPath("$.profile.department").value("Computer Science"))
                .andExpect(content().json("""
                        {"evaluation":{"status":"SCHEDULED","signupDeadline":"2026-10-31","course":"CS 3345.001",
                            "observer":"Keira Larkspur","observationDate":"2026-11-04","completedAt":null},
                         "schedule":{"rank":"ASSISTANT","intervalYears":1,"dueStatus":"Overdue"},
                         "termObservations":{"total":1,"completed":0,"scheduled":1},
                         "upcoming":[{"type":"OBSERVATION","date":"2026-11-04","startTime":"08:30:00","overdue":false,
                            "role":"OBSERVEE","status":"NOT_STARTED","course":"CS 3345.001","with":"Keira Larkspur"}],
                         "history":[],"observationsForOthers":[]}
                        """, JsonCompareMode.LENIENT))
                .andExpect(jsonPath("$.observationsForMe", hasSize(1)))
                .andExpect(jsonPath("$.observationsForMe[0].status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.observationsForMe[0].overdue").value(false))
                .andExpect(jsonPath("$.observationsForMe[0].semester").value("Fall 2026"));
    }

    @Test
    void anObserverSeesTheObservationTheyWillDoAndTheirOwnHistory() throws Exception {
        dashboard(KEIRA).andExpect(status().isOk()).andExpect(content().json("""
                        {"evaluation":{"status":"AWAITING_OBSERVER","course":"CS 3345.HON","observer":null},
                         "schedule":{"rank":"ASSISTANT","intervalYears":1,"nextDueSemester":"Fall 2026","dueStatus":"Due"},
                         "termObservations":{"total":1,"completed":0,"scheduled":1},
                         "upcoming":[{"type":"OBSERVATION","date":"2026-11-04","role":"OBSERVER",
                            "status":"NOT_STARTED","with":"Leander Coldwell"}],
                         "history":[{"term":"Fall","academicYear":2025,"status":"COMPLETED","observationCount":1}],
                         "observationsForMe":[{"course":"CS 3345.HON","semester":"Fall 2025","status":"COMPLETED",
                            "with":"Arlo Glimmerton"}],
                         "observationsForOthers":[{"course":"CS 3345.001","semester":"Fall 2026",
                            "status":"NOT_STARTED","with":"Leander Coldwell"}]}
                        """, JsonCompareMode.LENIENT));
    }

    @Test
    void dueFacultyWithoutASignupAreAskedToSignUpUntilTheDeadline() throws Exception {
        dashboard(HOLLIS)
                .andExpect(jsonPath("$.evaluation.status").value("NOT_SIGNED_UP"))
                .andExpect(jsonPath("$.upcoming", hasSize(1)))
                .andExpect(jsonPath("$.upcoming[0].type").value("SIGNUP"))
                .andExpect(jsonPath("$.upcoming[0].date").value("2026-10-31"));

        // After the deadline they still have not signed up, but there is nothing left to do about it here.
        clock.set("2026-11-02T15:00:00Z");
        dashboard(HOLLIS)
                .andExpect(jsonPath("$.evaluation.status").value("NOT_SIGNED_UP"))
                .andExpect(jsonPath("$.upcoming", hasSize(0)));
    }

    @Test
    void facultyWhoAreNotDueSeeTheirNextDueDate() throws Exception {
        dashboard(KAIA).andExpect(content().json("""
                        {"profile":{"name":"Kaia Clearbrook","rank":"FULL","department":"Computer Science",
                            "school":"Erik Jonsson School of Engineering and Computer Science"},
                         "evaluation":{"status":"NOT_DUE"},
                         "schedule":{"rank":"FULL","intervalYears":2,"nextDueDate":"2027-03-11",
                            "nextDueSemester":"Spring 2027","dueStatus":"Upcoming"},
                         "termObservations":{"total":0,"completed":0,"scheduled":0},
                         "upcoming":[]}
                        """, JsonCompareMode.LENIENT));
    }

    @Test
    void anObservationPastItsDateIsOverdueUntilTheObserverSubmitsIt() throws Exception {
        clock.set("2026-11-05T15:00:00Z");
        dashboard(LEANDER)
                .andExpect(jsonPath("$.evaluation.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.upcoming[0].overdue").value(true))
                .andExpect(jsonPath("$.observationsForMe[0].status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.observationsForMe[0].overdue").value(true));

        // Starting the form does not make it any less late.
        db.update("UPDATE observation SET status = 'IN_PROGRESS' WHERE signup_id = ?", signupOf(LEANDER));
        dashboard(KEIRA)
                .andExpect(jsonPath("$.upcoming[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.upcoming[0].overdue").value(true));

        submitLeandersObservation();
        dashboard(LEANDER)
                .andExpect(jsonPath("$.evaluation.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.termObservations.completed").value(1))
                .andExpect(jsonPath("$.termObservations.scheduled").value(0))
                .andExpect(jsonPath("$.upcoming[?(@.type == 'OBSERVATION')]", hasSize(0)))
                .andExpect(jsonPath("$.observationsForMe[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$.observationsForMe[0].overdue").value(false));
    }

    @Test
    void aCompletedRecordCompletesTheEvaluation() throws Exception {
        db.update("""
                UPDATE observation SET status = 'COMPLETED', completed_at = TIMESTAMP '2026-11-04 09:45:00'
                WHERE signup_id = ?
                """, signupOf(LEANDER));
        dashboard(LEANDER)
                .andExpect(jsonPath("$.evaluation.status").value("COMPLETED"))
                .andExpect(jsonPath("$.evaluation.completedAt").value("2026-11-04T09:45:00"))
                .andExpect(jsonPath("$.observationsForMe[0].status").value("COMPLETED"));
    }

    @Test
    void bothSidesOweProcessFeedbackOnASubmittedObservationUntilTheyGiveIt() throws Exception {
        clock.set("2026-11-05T15:00:00Z");
        submitLeandersObservation();
        for (int professor : List.of(LEANDER, KEIRA))
            dashboard(professor)
                    .andExpect(jsonPath("$.upcoming[?(@.type == 'FEEDBACK')]", hasSize(1)))
                    .andExpect(
                            jsonPath("$.upcoming[?(@.type == 'FEEDBACK')].date").value("2026-12-01"))
                    .andExpect(jsonPath("$.upcoming[?(@.type == 'FEEDBACK')].overdue")
                            .value(false))
                    .andExpect(jsonPath("$.upcoming[?(@.type == 'OBSERVATION')]", hasSize(0)));

        db.update("""
                INSERT INTO process_feedback (cycle_id, professor_id, signup_id, overall_rating, submitted_date)
                VALUES (1, ?, ?, 4, DATE '2026-11-05')
                """, KEIRA, signupOf(LEANDER));
        dashboard(KEIRA).andExpect(jsonPath("$.upcoming[?(@.type == 'FEEDBACK')]", hasSize(0)));
        dashboard(LEANDER).andExpect(jsonPath("$.upcoming[?(@.type == 'FEEDBACK')]", hasSize(1)));

        clock.set("2026-12-02T15:00:00Z");
        dashboard(LEANDER)
                .andExpect(
                        jsonPath("$.upcoming[?(@.type == 'FEEDBACK')].overdue").value(true));
    }

    @Test
    void aCancelledSignupDropsOutOfTheDashboard() throws Exception {
        db.update("UPDATE evaluation_signup SET status = 'CANCELLED' WHERE signup_id = ?", signupOf(KEIRA));
        dashboard(KEIRA)
                .andExpect(jsonPath("$.evaluation.status").value("NOT_SIGNED_UP"))
                .andExpect(jsonPath("$.upcoming[?(@.type == 'SIGNUP')]", hasSize(1)));
    }

    @Test
    void professorsCanOnlySeeTheirOwnDashboard() throws Exception {
        mvc.perform(get(path(KEIRA)).with(user(professor(KEIRA)))).andExpect(status().isOk());
        mvc.perform(get(path(KEIRA)).with(user(professor(DARIO)))).andExpect(status().isForbidden());
        mvc.perform(get(path(KEIRA))).andExpect(status().isUnauthorized());
    }

    /** What the observation form's Submit does: the observer's part is over. */
    private void submitLeandersObservation() {
        db.update("""
                UPDATE observation SET status = 'SUBMITTED', observer_signed = TRUE,
                    observer_signed_at = TIMESTAMP '2026-11-04 09:45:00'
                WHERE signup_id = ?
                """, signupOf(LEANDER));
    }

    private int signupOf(int professorId) {
        return db.queryForObject("""
                SELECT es.signup_id FROM evaluation_signup es JOIN observee oe ON oe.observee_id = es.observee_id
                WHERE oe.professor_id = ? AND oe.cycle_id = 1
                """, Integer.class, professorId);
    }

    private ResultActions dashboard(int professorId) throws Exception {
        return mvc.perform(get(path(professorId)).with(user(professor(professorId))));
    }

    private static String path(int professorId) {
        return "/api/professors/" + professorId + "/dashboard";
    }

    private static AccountPrincipal professor(int professorId) {
        return new AccountPrincipal(
                new CurrentUser(
                        100 + professorId,
                        professorId,
                        1,
                        "Professor " + professorId,
                        "professor" + professorId + "@example.edu",
                        List.of(Role.PROFESSOR)),
                null,
                true);
    }
}
