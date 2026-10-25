package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.utdallas.cs4485.assessmenthelper.entity.Department;
import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.entity.School;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

@DataJdbcTest
@Import(ProfessorService.class)
class ProfessorServiceTests {

    @Autowired
    private ProfessorService service;

    @Autowired
    private JdbcAggregateTemplate db;

    private School school;
    private Department testDepartment;
    private Department otherDepartment;

    @BeforeEach
    void createDepartments() {
        school = db.insert(new School(null, "Test School"));
        testDepartment = db.insert(new Department(null, school.schoolId(), "Test Department", "TEST"));
        otherDepartment = db.insert(new Department(null, school.schoolId(), "Other Department", "OTHER"));
    }

    @Test
    void returnsOnlyTheSchoolsProfessorsSortedByLastThenFirstName() {
        Professor zedStub = db.insert(professor(testDepartment, "Zed", "Stub", true));
        Professor beaMock = db.insert(professor(testDepartment, "Bea", "Mock", true));
        Professor amyStub = db.insert(professor(testDepartment, "Amy", "Stub", true));
        db.insert(professor(otherDepartment, "Otto", "Fixture", true));

        assertThat(service.findBySchool("TEST")).containsExactly(beaMock, amyStub, zedStub);
    }

    @Test
    void includesInactiveProfessors() {
        Professor retired = db.insert(professor(testDepartment, "Ray", "Retired", false));
        Professor active = db.insert(professor(testDepartment, "Ann", "Active", true));

        assertThat(service.findBySchool("TEST")).containsExactly(active, retired);
    }

    @Test
    void schoolLookupIgnoresCaseAndSurroundingWhitespace() {
        Professor professor = db.insert(professor(testDepartment, "Sam", "Sample", true));

        assertThat(service.findBySchool("  test ")).containsExactly(professor);
    }

    @Test
    void schoolWithoutProfessorsReturnsEmptyList() {
        db.insert(new Department(null, school.schoolId(), "Empty Department", "EMPTY"));

        assertThat(service.findBySchool("EMPTY")).isEmpty();
    }

    @Test
    void unknownSchoolThrowsNotFoundWithStrippedCode() {
        assertThatThrownBy(() -> service.findBySchool("  NOPE "))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No school with code NOPE");
    }

    private Professor professor(Department department, String firstName, String lastName, boolean active) {
        String email = firstName.toLowerCase() + "." + lastName.toLowerCase() + "@test.edu";
        return new Professor(null, department.departmentId(), null, firstName, lastName, email, null, active);
    }
}
