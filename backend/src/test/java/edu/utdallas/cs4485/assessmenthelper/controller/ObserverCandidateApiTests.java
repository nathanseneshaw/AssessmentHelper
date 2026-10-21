package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.entity.Course;
import edu.utdallas.cs4485.assessmenthelper.entity.CourseSection;
import edu.utdallas.cs4485.assessmenthelper.entity.Department;
import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.entity.Semester;
import edu.utdallas.cs4485.assessmenthelper.entity.TeachingHistory;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends requests to {@code GET /api/observer-candidates} through the whole application: controller, service and the
 * H2 database, with nothing mocked. Each test runs in a transaction that is rolled back afterwards, and only uses rows
 * it creates in a TEST department, so the demo rows from data.sql do not affect the results.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ObserverCandidateApiTests {

    private static final Map<String, String> VALID_PARAMS =
            Map.of("courseNumber", "TEST 3345", "sectionNumber", "001", "professorName", "Olivia Observee");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcAggregateTemplate db;

    private Department department;
    private Semester fall;
    private Course course4348;
    private Professor higher;
    private Professor same;

    /**
     * Olivia Observee teaches TEST 3345.001 on TR 10:00-11:15. Of the other professors, only Hannah Higher (4000 level)
     * and Sam Same (3000 level) can observe her.
     */
    @BeforeEach
    void createData() {
        department = db.insert(new Department(null, "Test Department", "TEST"));
        Department otherDepartment = db.insert(new Department(null, "Other Department", "OTHER"));
        fall = db.insert(new Semester(null, "Fall", 2026, LocalDate.of(2026, 8, 24), LocalDate.of(2026, 12, 11)));
        Course course1337 = course(department, "1337");
        Course course3345 = course(department, "3345");
        course4348 = course(department, "4348");
        Course otherCourse4348 = course(otherDepartment, "4348");

        teach(professor(department, "Olivia", "Observee"), course3345, "001", "TR", "10:00", "11:15");

        higher = professor(department, "Hannah", "Higher");
        teach(higher, course4348, "001", "MW", "13:00", "14:15");
        same = professor(department, "Sam", "Same");
        teach(same, course3345, "002", "MW", "13:00", "14:15");

        // Only teaches below the observee's level.
        teach(professor(department, "Larry", "Lower"), course1337, "001", "MW", "13:00", "14:15");
        // Teaches during the observee's class.
        teach(professor(department, "Olga", "Overlap"), course4348, "002", "TR", "11:00", "12:15");
        // Teaches a higher level at a free time, but in another department.
        teach(professor(otherDepartment, "Otto", "Other"), otherCourse4348, "001", "MW", "13:00", "14:15");
    }

    @Test
    void everyParameterPresentReturnsTheEligibleObservers() throws Exception {
        mockMvc.perform(validRequest())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].professorId").value(higher.professorId()))
                .andExpect(jsonPath("$[0].firstName").value("Hannah"))
                .andExpect(jsonPath("$[0].lastName").value("Higher"))
                .andExpect(jsonPath("$[0].email").value("hannah.higher@test.edu"))
                .andExpect(jsonPath("$[0].departmentCode").value("TEST"))
                .andExpect(jsonPath("$[0].courseLevel").value(4000))
                .andExpect(jsonPath("$[1].professorId").value(same.professorId()))
                .andExpect(jsonPath("$[1].firstName").value("Sam"))
                .andExpect(jsonPath("$[1].lastName").value("Same"))
                .andExpect(jsonPath("$[1].email").value("sam.same@test.edu"))
                .andExpect(jsonPath("$[1].departmentCode").value("TEST"))
                .andExpect(jsonPath("$[1].courseLevel").value(3000));
    }

    @Test
    void moreThanFiveEligibleObserversReturnsFive() throws Exception {
        for (int i = 1; i <= 5; i++) {
            teach(professor(department, "Extra", "Number" + i), course4348, "10" + i, "MW", "15:00", "16:15");
        }

        mockMvc.perform(validRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @ParameterizedTest(name = "blank {0}")
    @ValueSource(strings = {"courseNumber", "sectionNumber", "professorName"})
    void blankParameterReturns400ProblemDetail(String blank) throws Exception {
        Map<String, String> params = new HashMap<>(VALID_PARAMS);
        params.put(blank, "  ");

        mockMvc.perform(request(params))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(blank + " must not be blank"));
    }

    private static MockHttpServletRequestBuilder validRequest() {
        return request(VALID_PARAMS);
    }

    private static MockHttpServletRequestBuilder request(Map<String, String> params) {
        MockHttpServletRequestBuilder request = get("/api/observer-candidates");
        params.forEach((name, value) -> request.param(name, value));
        return request;
    }

    private Course course(Department owner, String courseNumber) {
        return db.insert(new Course(
                null,
                owner.departmentId(),
                owner.departmentCode(),
                courseNumber,
                "Course " + courseNumber,
                "UNDERGRADUATE"));
    }

    private Professor professor(Department owner, String firstName, String lastName) {
        String email = (firstName + "." + lastName + "@test.edu").toLowerCase(Locale.ROOT);
        return db.insert(new Professor(null, owner.departmentId(), null, firstName, lastName, email, null, true));
    }

    private void teach(
            Professor professor, Course course, String sectionNumber, String meetingDays, String start, String end) {
        CourseSection section = db.insert(new CourseSection(
                null,
                course.courseId(),
                fall.semesterId(),
                sectionNumber,
                meetingDays,
                LocalTime.parse(start),
                LocalTime.parse(end),
                null));
        db.insert(new TeachingHistory(null, professor.professorId(), section.sectionId()));
    }
}
