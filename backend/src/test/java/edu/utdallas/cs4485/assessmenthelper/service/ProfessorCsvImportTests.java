package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.assertThat;

import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.repository.DepartmentRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.service.ImportResult.RowError;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

@DataJdbcTest
@Import({
    CsvImportService.class,
    ProfessorCsvImporter.class,
    SectionCsvImporter.class,
    edu.utdallas.cs4485.assessmenthelper.config.ClockConfig.class
})
class ProfessorCsvImportTests {

    private static final String HEADER = "email,first_name,last_name,department_code,rank,hire_date,active";
    private static final String WREN = "wren.quillfeather@example.edu";
    private static final String ORLA = "orla.fernsby@example.edu";
    private static final String ORLA_ROW = ORLA + ",Orla,Fernsby,MATH,ASSOCIATE,2019-08-15,";
    private static final String PELL = "pell.marlowe@example.edu";

    @Autowired
    private CsvImportService service;

    @Autowired
    private ProfessorRepository professors;

    @Autowired
    private DepartmentRepository departments;

    @Autowired
    private JdbcAggregateTemplate db;

    private Professor wren;

    @BeforeEach
    void addAnInactiveAssistantProfessor() {
        wren = db.insert(new Professor(
                null, departmentId("CS"), "ASSISTANT", "Wren", "Quillfeather", WREN, LocalDate.of(2020, 8, 17), false));
    }

    @Test
    void createsANewProfessorWithALowercaseEmail() {
        ImportResult result = save(HEADER, "Orla.Fernsby@Example.edu,Orla,Fernsby,math,associate,2019-08-15,true");

        assertThat(result.errors()).isEmpty();
        assertThat(result.saved()).isTrue();
        assertThat(result.rowCount()).isEqualTo(1);
        Professor orla = professor(ORLA).orElseThrow();
        assertThat(orla)
                .extracting(
                        Professor::departmentId,
                        Professor::professorRank,
                        Professor::firstName,
                        Professor::lastName,
                        Professor::email,
                        Professor::hireDate,
                        Professor::activeStatus)
                .containsExactly(
                        departmentId("MATH"), "ASSOCIATE", "Orla", "Fernsby", ORLA, LocalDate.of(2019, 8, 15), true);
    }

    @Test
    void updatesTheProfessorWithTheSameEmailIgnoringCase() {
        long before = professors.count();

        ImportResult result = save(HEADER, "WREN.Quillfeather@EXAMPLE.edu,Wren,Quillfeather-Hale,MATH,,,");

        assertThat(result.errors()).isEmpty();
        assertThat(professors.count()).isEqualTo(before);
        Professor updated = professors.findById(wren.professorId()).orElseThrow();
        assertThat(updated.lastName()).isEqualTo("Quillfeather-Hale");
        assertThat(updated.departmentId()).isEqualTo(departmentId("MATH"));
        assertThat(updated.email()).isEqualTo(WREN);
    }

    @Test
    void anUnknownDepartmentIsAnErrorAndNothingIsSaved() {
        ImportResult result = save(HEADER, ORLA_ROW, WREN + ",Wren,Renamed,CSS,,,");

        assertThat(result.saved()).isFalse();
        assertThat(result.errors())
                .containsExactly(new RowError(3, "department_code", "Unknown department code 'CSS'."));
        assertThat(professor(ORLA)).isEmpty();
        assertThat(professors.findById(wren.professorId())).contains(wren);
    }

    @Test
    void anActiveProfessorNeedsARankAndAHireDate() {
        ImportResult result = save(HEADER, ORLA + ",Orla,Fernsby,MATH,,,", PELL + ",Pell,Marlowe,CS,FULL,,true");

        assertThat(result.saved()).isFalse();
        assertThat(result.errors())
                .containsExactly(
                        new RowError(2, "rank", "An active professor needs a rank."),
                        new RowError(2, "hire_date", "An active professor needs a hire date."),
                        new RowError(3, "hire_date", "An active professor needs a hire date."));
        assertThat(professor(ORLA)).isEmpty();
    }

    @Test
    void badCellsAreReportedWithTheirRowAndColumn() {
        ImportResult result = save(
                HEADER,
                "not-an-email,Orla,Fernsby,MATH,,,",
                ORLA + ",,Fernsby,MATH,,2019-02-30,maybe",
                PELL + ",Pell,Marlowe,CS,,15/8/2019,");

        assertThat(result.errors())
                .containsExactly(
                        new RowError(2, "email", "'not-an-email' is not an email address."),
                        new RowError(3, "first_name", "first_name is required."),
                        new RowError(3, "hire_date", "'2019-02-30' is not a date. Use YYYY-MM-DD or M/D/YYYY."),
                        new RowError(3, "active", "'maybe' is not true or false."),
                        new RowError(4, "hire_date", "'15/8/2019' is not a date. Use YYYY-MM-DD or M/D/YYYY."));
    }

    @Test
    void anExtraCommaIsAnErrorInsteadOfDroppingTheLastCell() {
        ImportResult result = save(HEADER, ORLA_ROW, WREN + ",Wren,Quillfeather,CS,,,,true");

        assertThat(result.saved()).isFalse();
        assertThat(result.errors())
                .containsExactly(new RowError(
                        3,
                        null,
                        "This row has 8 cells, but the header has 7 columns. "
                                + "If a value contains a comma, put it in double quotes, like \"Smith, Jr.\"."));
        assertThat(professor(ORLA)).isEmpty();
        assertThat(professors.findById(wren.professorId())).contains(wren);
    }

    @Test
    void theHeaderMustHaveTheTemplatesColumns() {
        ImportResult result = save("email,first_name,department_code,hire_dat", ORLA + ",Orla,MATH,2019-08-15");

        assertThat(result.rowCount()).isZero();
        assertThat(result.errors())
                .containsExactly(
                        new RowError(null, "hire_dat", "Unknown column 'hire_dat'."),
                        new RowError(null, "last_name", "Missing column 'last_name'."),
                        new RowError(null, "rank", "Missing column 'rank'."),
                        new RowError(null, "hire_date", "Missing column 'hire_date'."),
                        new RowError(null, "active", "Missing column 'active'."));
        assertThat(professor(ORLA)).isEmpty();
    }

    private ImportResult save(String... lines) {
        return service.importProfessors(csv(lines));
    }

    static byte[] csv(String... lines) {
        return String.join("\r\n", lines).getBytes(StandardCharsets.UTF_8);
    }

    private Optional<Professor> professor(String email) {
        return professors.findAll().stream()
                .filter(professor -> professor.email().equals(email))
                .findFirst();
    }

    private Integer departmentId(String code) {
        return departments.findByDepartmentCodeIgnoreCase(code).orElseThrow().departmentId();
    }
}
