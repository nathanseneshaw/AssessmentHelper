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

    private Department testDepartment;
    private Department otherDepartment;

    @BeforeEach
    void createDepartments() {
        School school = db.insert(new School(null, "Test School"));
        testDepartment = db.insert(new Department(null, school.schoolId(), "Test Department", "TEST"));
        otherDepartment = db.insert(new Department(null, school.schoolId(), "Other Department", "OTHER"));
    }

    @Test
    void returnsOnlyTheSchoolsProfessorsIncludingInactiveSortedByLastThenFirstName() {
        Professor zedStub = db.insert(professor(testDepartment, "Zed", "Stub", false));
        Professor beaMock = db.insert(professor(testDepartment, "Bea", "Mock", true));
        Professor amyStub = db.insert(professor(testDepartment, "Amy", "Stub", true));
        db.insert(professor(otherDepartment, "Otto", "Fixture", true));

        assertThat(service.findBySchool("TEST")).containsExactly(beaMock, amyStub, zedStub);
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
