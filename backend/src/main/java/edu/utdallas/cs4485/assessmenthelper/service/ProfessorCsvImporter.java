package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.entity.Department;
import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationPolicy;
import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.repository.DepartmentRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationPolicyRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.service.CsvImportService.Row;
import edu.utdallas.cs4485.assessmenthelper.service.ImportResult.RowError;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
class ProfessorCsvImporter {

    static final List<String> COLUMNS =
            List.of("email", "first_name", "last_name", "department_code", "rank", "hire_date", "active");

    private static final DateTimeFormatter EXCEL_DATE =
            DateTimeFormatter.ofPattern("M/d/uuuu").withResolverStyle(ResolverStyle.STRICT);

    private final ProfessorRepository professors;
    private final DepartmentRepository departments;
    private final EvaluationPolicyRepository policies;

    ProfessorCsvImporter(
            ProfessorRepository professors, DepartmentRepository departments, EvaluationPolicyRepository policies) {
        this.professors = professors;
        this.departments = departments;
        this.policies = policies;
    }

    void importRow(Row row, List<RowError> errors) {
        int before = errors.size();
        String email = row.required("email", errors);
        if (email != null && !email.contains("@"))
            errors.add(row.error("email", "'" + email + "' is not an email address."));
        String firstName = row.required("first_name", errors);
        String lastName = row.required("last_name", errors);
        Department department = department(row, errors);
        String rank = rank(row, errors);
        LocalDate hireDate = hireDate(row, errors);
        Boolean active = active(row, errors);
        if (errors.size() > before) return;

        Optional<Professor> stored = professors.findByEmailIgnoreCase(email);
        Professor professor = new Professor(
                stored.map(Professor::professorId).orElse(null),
                department.departmentId(),
                rank != null ? rank : stored.map(Professor::professorRank).orElse(null),
                firstName,
                lastName,
                stored.map(Professor::email).orElse(email.toLowerCase(Locale.ROOT)),
                hireDate != null ? hireDate : stored.map(Professor::hireDate).orElse(null),
                active != null ? active : stored.map(Professor::activeStatus).orElse(true));
        if (Boolean.TRUE.equals(professor.activeStatus())) {
            if (professor.professorRank() == null) errors.add(row.error("rank", "An active professor needs a rank."));
            if (professor.hireDate() == null)
                errors.add(row.error("hire_date", "An active professor needs a hire date."));
        }
        if (errors.size() == before) professors.save(professor);
    }

    private Department department(Row row, List<RowError> errors) {
        String code = row.required("department_code", errors);
        if (code == null) return null;
        Optional<Department> department = departments.findByDepartmentCodeIgnoreCase(code);
        if (department.isEmpty()) errors.add(row.error("department_code", "Unknown department code '" + code + "'."));
        return department.orElse(null);
    }

    private String rank(Row row, List<RowError> errors) {
        String rank = row.get("rank");
        if (rank == null) return null;
        List<String> ranks =
                policies.findAll().stream().map(EvaluationPolicy::professorRank).toList();
        if (ranks.contains(rank.toUpperCase(Locale.ROOT))) return rank.toUpperCase(Locale.ROOT);
        errors.add(row.error("rank", "Unknown rank '" + rank + "'. Use " + String.join(", ", ranks) + "."));
        return null;
    }

    private static LocalDate hireDate(Row row, List<RowError> errors) {
        String value = row.get("hire_date");
        if (value == null) return null;
        try {
            return LocalDate.parse(value, value.contains("/") ? EXCEL_DATE : DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            errors.add(row.error("hire_date", "'" + value + "' is not a date. Use YYYY-MM-DD or M/D/YYYY."));
            return null;
        }
    }

    private static Boolean active(Row row, List<RowError> errors) {
        String value = row.get("active");
        if (value == null) return null;
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        errors.add(row.error("active", "'" + value + "' is not true or false."));
        return null;
    }
}
