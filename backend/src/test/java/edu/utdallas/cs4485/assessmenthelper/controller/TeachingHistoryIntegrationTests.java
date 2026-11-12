package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.entity.Course;
import edu.utdallas.cs4485.assessmenthelper.entity.CourseSection;
import edu.utdallas.cs4485.assessmenthelper.entity.Department;
import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.entity.School;
import edu.utdallas.cs4485.assessmenthelper.entity.Semester;
import edu.utdallas.cs4485.assessmenthelper.entity.TeachingHistory;
import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** Runs the real access rule: a professor reads their own teaching history, the committee reads anyone's. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TeachingHistoryIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcAggregateTemplate db;

    private Professor ann;
    private Professor bea;

    @BeforeEach
    void createProfessorsWithOneSectionEach() {
        School school = db.insert(new School(null, "Test School"));
        Department department = db.insert(new Department(null, school.schoolId(), "Test Department", "TEST"));
        Semester semester =
                db.insert(new Semester(null, "Fall", 2026, LocalDate.of(2026, 8, 24), LocalDate.of(2026, 12, 16)));
        Course course =
                db.insert(new Course(null, department.departmentId(), "CS", "4485", "Computer Science Project", null));
        ann = db.insert(new Professor(
                null, department.departmentId(), "ASSISTANT", "Ann", "Test", "ann.test@test.edu", null, true));
        bea = db.insert(new Professor(
                null, department.departmentId(), "ASSISTANT", "Bea", "Test", "bea.test@test.edu", null, true));
        for (Professor professor : List.of(ann, bea)) {
            CourseSection section = db.insert(
                    new CourseSection(null, course.courseId(), semester.semesterId(), "001", null, null, null, null));
            db.insert(new TeachingHistory(null, professor.professorId(), section.sectionId()));
        }
    }

    @Test
    void aProfessorReadsTheirOwnTeachingHistory() throws Exception {
        history(ann.professorId(), principal(ann.professorId(), Role.PROFESSOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseNumber").value("4485"))
                .andExpect(jsonPath("$[0].level").value(4000))
                .andExpect(jsonPath("$[0].term").value("Fall"))
                .andExpect(jsonPath("$[0].academicYear").value(2026));
    }

    @Test
    void aProfessorCannotReadAnotherProfessorsTeachingHistory() throws Exception {
        history(bea.professorId(), principal(ann.professorId(), Role.PROFESSOR)).andExpect(status().isForbidden());
    }

    @Test
    void aCommitteeMemberReadsAnyProfessorsTeachingHistory() throws Exception {
        history(bea.professorId(), principal(ann.professorId(), Role.PROFESSOR, Role.AC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void anUnknownProfessorReturns404ForTheCommittee() throws Exception {
        history(-1, principal(ann.professorId(), Role.PROFESSOR, Role.AC))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No professor with id -1"));
    }

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/professors/" + ann.professorId() + "/teaching-history"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions history(int professorId, AccountPrincipal principal) throws Exception {
        return mvc.perform(
                get("/api/professors/" + professorId + "/teaching-history").with(user(principal)));
    }

    private static AccountPrincipal principal(int professorId, Role... roles) {
        return new AccountPrincipal(
                new CurrentUser(
                        1000 + professorId,
                        professorId,
                        1,
                        "Professor " + professorId,
                        "professor" + professorId + "@example.edu",
                        List.of(roles)),
                null,
                true);
    }
}
