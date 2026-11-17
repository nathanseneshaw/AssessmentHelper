package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs on data.sql's Fall 2026 demo (evaluation cycle 1, signups close 2026-10-31). Keira (41) is due and signed up
 * with no observer yet, and observes Leander on 2026-11-04. The dashboard's rules are covered by
 * FacultyDashboardServiceTests; this checks the endpoint end to end.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AcDashboardIntegrationTests.Time.class)
@Transactional
class FacultyDashboardIntegrationTests {
    private static final String TODAY = "2026-10-26T15:00:00Z";
    private static final int KEIRA = 41;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AcDashboardIntegrationTests.MovableClock clock;

    @BeforeEach
    void resetClock() {
        clock.set(TODAY);
    }

    @Test
    void dashboardShowsUpcomingObservationAndHistoryWhenProfessorIsAnObserver() throws Exception {
        mvc.perform(get("/api/professors/" + KEIRA + "/dashboard").with(user(professor(KEIRA))))
                .andExpect(status().isOk())
                .andExpect(content().json("""
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
