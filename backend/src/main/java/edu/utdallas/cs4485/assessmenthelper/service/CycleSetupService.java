package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationCycle;
import edu.utdallas.cs4485.assessmenthelper.entity.Semester;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationCycleRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.SemesterRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates a future cycle and its semester roster in one transaction. */
@Service
public class CycleSetupService {
    private final SemesterRepository semesters;
    private final EvaluationCycleRepository cycles;
    private final ProfessorRepository professors;
    private final CsvImportService imports;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public CycleSetupService(
            SemesterRepository semesters,
            EvaluationCycleRepository cycles,
            ProfessorRepository professors,
            CsvImportService imports,
            JdbcTemplate jdbc,
            Clock clock) {
        this.semesters = semesters;
        this.cycles = cycles;
        this.professors = professors;
        this.imports = imports;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record Setup(
            String term,
            int year,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate signupDeadline,
            LocalDate observationDeadline,
            LocalDate feedbackDeadline) {}

    public record SemesterOption(
            String term, int year, LocalDate startDate, LocalDate endDate, boolean estimated, boolean hasCycle) {}

    public List<SemesterOption> upcomingSemesters() {
        LocalDate today = LocalDate.now(clock);
        var candidates = new ArrayList<SemesterOption>();
        var existingCycles = cycles.findAll();
        for (int year = today.getYear(); year <= today.getYear() + 3; year++) {
            for (String term : List.of("Spring", "Summer", "Fall")) {
                var existing = semesters.findFirstByTermIgnoreCaseAndAcademicYearOrderBySemesterId(term, year);
                int month = term.equals("Spring") ? 1 : term.equals("Summer") ? 6 : 8;
                int day = term.equals("Spring") ? 20 : term.equals("Summer") ? 1 : 24;
                LocalDate start = existing.map(Semester::startDate).orElse(LocalDate.of(year, month, day));
                LocalDate end = existing.map(Semester::endDate)
                        .orElse(LocalDate.of(
                                year,
                                term.equals("Spring") ? 5 : term.equals("Summer") ? 8 : 12,
                                term.equals("Spring") ? 15 : term.equals("Summer") ? 14 : 16));
                if (start.isAfter(today))
                    candidates.add(new SemesterOption(
                            term,
                            year,
                            start,
                            end,
                            existing.isEmpty()
                                    || existing.get().startDate() == null
                                    || existing.get().endDate() == null,
                            existing.isPresent()
                                    && existingCycles.stream()
                                            .anyMatch(cycle -> cycle.semesterId()
                                                    .equals(existing.get().semesterId()))));
            }
        }
        return candidates.subList(0, Math.min(6, candidates.size()));
    }

    @Transactional
    public EvaluationCycle create(Setup setup, byte[] professorCsv, byte[] sectionCsv) {
        lockSetup();
        validate(setup, false);
        var existing = semesters.findFirstByTermIgnoreCaseAndAcademicYearOrderBySemesterId(setup.term(), setup.year());
        if (existing.isPresent()
                && cycles.findAll().stream()
                        .anyMatch(cycle ->
                                cycle.semesterId().equals(existing.get().semesterId())))
            throw new IllegalArgumentException("A cycle already exists for this semester.");
        validateSectionSemester(sectionCsv, setup);
        validateTeachingAssignments(professorCsv, sectionCsv);
        Semester semester = semesters.save(new Semester(
                existing.map(Semester::semesterId).orElse(null),
                setup.term(),
                setup.year(),
                setup.startDate(),
                setup.endDate()));
        EvaluationCycle cycle = cycles.save(new EvaluationCycle(
                null,
                semester.semesterId(),
                setup.signupDeadline(),
                setup.observationDeadline(),
                setup.feedbackDeadline(),
                "OPEN"));
        importRoster(cycle, professorCsv, sectionCsv);
        jdbc.update("INSERT INTO cycle_readiness (cycle_id, state) VALUES (?, 'PENDING')", cycle.cycleId());
        if (!LocalDate.now(clock).isBefore(semester.startDate()))
            throw new ConflictException("A cycle must be created before its semester starts.");
        return cycle;
    }

    private void importRoster(EvaluationCycle cycle, byte[] professorCsv, byte[] sectionCsv) {
        var originalProfiles = new java.util.HashMap<String, edu.utdallas.cs4485.assessmenthelper.entity.Professor>();
        for (String email : emails(professorCsv))
            professors.findByEmailIgnoreCase(email).ifPresent(professor -> originalProfiles.put(email, professor));
        ImportResult professorsResult = imports.importCycleProfessors(professorCsv);
        if (!professorsResult.saved()) throw new CycleImportException("professors", professorsResult);
        ImportResult sectionsResult = imports.importSections(sectionCsv);
        if (!sectionsResult.saved()) throw new CycleImportException("sections", sectionsResult);
        for (String email : emails(professorCsv)) {
            var professor = professors.findByEmailIgnoreCase(email).orElseThrow();
            boolean newProfessor = !originalProfiles.containsKey(email);
            if (newProfessor)
                jdbc.update("INSERT INTO cycle_professor_origin (professor_id) VALUES (?)", professor.professorId());
            jdbc.update(
                    """
                    INSERT INTO cycle_professor_roster
                    (cycle_id, professor_id, department_id, professor_rank, first_name, last_name, email, hire_date, active_status, new_professor)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    cycle.cycleId(),
                    professor.professorId(),
                    professor.departmentId(),
                    professor.professorRank(),
                    professor.firstName(),
                    professor.lastName(),
                    professor.email(),
                    professor.hireDate(),
                    professor.activeStatus(),
                    newProfessor);
        }
        // Future roster changes must not replace the profile used by the current semester.
        originalProfiles.values().forEach(professors::save);
    }

    @Transactional
    public EvaluationCycle replaceFiles(int cycleId, byte[] professorCsv, byte[] sectionCsv) {
        var cycle = cycles.findForUpdateByCycleId(cycleId)
                .orElseThrow(() -> new NotFoundException("Evaluation cycle not found."));
        var semester =
                semesters.findById(cycle.semesterId()).orElseThrow(() -> new NotFoundException("Semester not found."));
        if (semester.startDate() == null || !LocalDate.now(clock).isBefore(semester.startDate()))
            throw new ConflictException("A cycle cannot be edited after its semester starts.");
        Integer signups = jdbc.queryForObject("""
                SELECT COUNT(*) FROM evaluation_signup es JOIN observee oe ON oe.observee_id = es.observee_id
                WHERE oe.cycle_id = ?
                """, Integer.class, cycleId);
        if (signups != null && signups > 0)
            throw new ConflictException("A cycle with existing signups cannot replace its files.");
        var details = new Setup(
                semester.term(),
                semester.academicYear(),
                semester.startDate(),
                semester.endDate(),
                cycle.signupDeadline(),
                cycle.observationDeadline(),
                cycle.feedbackDeadline());
        validateSectionSemester(sectionCsv, details);
        validateTeachingAssignments(professorCsv, sectionCsv);
        // Replace the teaching assignments belonging to the previous roster for this future semester.
        jdbc.update("""
                DELETE FROM teaching_history WHERE professor_id IN
                (SELECT professor_id FROM cycle_professor_roster WHERE cycle_id = ?)
                AND section_id IN (SELECT section_id FROM course_section WHERE semester_id = ?)
                """, cycleId, semester.semesterId());
        jdbc.update("DELETE FROM cycle_professor_roster WHERE cycle_id = ?", cycleId);
        importRoster(cycle, professorCsv, sectionCsv);
        if (!LocalDate.now(clock).isBefore(semester.startDate()))
            throw new ConflictException("A cycle cannot be edited after its semester starts.");
        return cycle;
    }

    @Transactional
    public EvaluationCycle updateDates(int cycleId, Setup setup) {
        lockSetup();
        var cycle = cycles.findForUpdateByCycleId(cycleId)
                .orElseThrow(() -> new NotFoundException("Evaluation cycle not found."));
        var semester =
                semesters.findById(cycle.semesterId()).orElseThrow(() -> new NotFoundException("Semester not found."));
        if (semester.startDate() == null || !LocalDate.now(clock).isBefore(semester.startDate()))
            throw new ConflictException("A cycle cannot be edited after its semester starts.");
        if (setup == null || !semester.term().equalsIgnoreCase(setup.term()) || semester.academicYear() != setup.year())
            throw new IllegalArgumentException("The semester of a cycle cannot be changed.");
        validate(setup, true);
        semesters.save(new Semester(
                semester.semesterId(), semester.term(), semester.academicYear(), setup.startDate(), setup.endDate()));
        return cycles.save(new EvaluationCycle(
                cycle.cycleId(),
                cycle.semesterId(),
                setup.signupDeadline(),
                setup.observationDeadline(),
                setup.feedbackDeadline(),
                cycle.status()));
    }

    private void lockSetup() {
        jdbc.queryForObject("SELECT lock_id FROM cycle_setup_lock WHERE lock_id = 1 FOR UPDATE", Integer.class);
    }

    private void validate(Setup s, boolean existingCycle) {
        if (s == null
                || s.term() == null
                || s.startDate() == null
                || s.endDate() == null
                || s.signupDeadline() == null
                || s.observationDeadline() == null
                || s.feedbackDeadline() == null)
            throw new IllegalArgumentException("Semester dates and all three deadlines are required.");
        String term = s.term().toLowerCase(Locale.ROOT);
        if (!List.of("spring", "summer", "fall").contains(term)
                || s.startDate().getYear() != s.year()
                || s.endDate().getYear() != s.year()) throw new IllegalArgumentException("Invalid semester.");
        if (!s.startDate().isAfter(LocalDate.now(clock)))
            throw new IllegalArgumentException("A cycle must be created before its semester starts.");
        if (upcomingSemesters().stream()
                .noneMatch(option -> option.term().equalsIgnoreCase(term)
                        && option.year() == s.year()
                        && (existingCycle || !option.hasCycle())))
            throw new IllegalArgumentException("Choose one of the next six semesters.");
        if (s.endDate().isBefore(s.startDate())
                || s.signupDeadline().isBefore(s.startDate())
                || !s.observationDeadline().isAfter(s.signupDeadline())
                || !s.feedbackDeadline().isAfter(s.observationDeadline())
                || s.feedbackDeadline().isAfter(s.endDate()))
            throw new IllegalArgumentException("Deadlines must be in order and within the semester.");
        boolean overlaps = semesters.findAll().stream()
                .filter(other -> !(other.term().equalsIgnoreCase(s.term()) && other.academicYear() == s.year()))
                .filter(other -> other.startDate() != null && other.endDate() != null)
                .anyMatch(other ->
                        !s.startDate().isAfter(other.endDate()) && !s.endDate().isBefore(other.startDate()));
        if (overlaps) throw new IllegalArgumentException("Semester dates cannot overlap another semester.");
    }

    private static List<String> emails(byte[] csv) {
        try (CSVParser parser = CSVParser.parse(
                csvText(csv),
                CSVFormat.DEFAULT
                        .builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setTrim(true)
                        .get())) {
            List<String> emails = new ArrayList<>();
            for (var row : parser) {
                String email = row.get("email").strip().toLowerCase(Locale.ROOT);
                if (!emails.contains(email)) emails.add(email);
            }
            return emails;
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Could not read professor emails.", e);
        }
    }

    private static void validateSectionSemester(byte[] csv, Setup s) {
        try (CSVParser parser = CSVParser.parse(
                csvText(csv),
                CSVFormat.DEFAULT
                        .builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setTrim(true)
                        .get())) {
            for (var row : parser) {
                if (!row.get("term").strip().equalsIgnoreCase(s.term())
                        || !row.get("year").strip().equals(Integer.toString(s.year())))
                    throw new IllegalArgumentException("Every section must belong to the selected semester.");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read sections CSV.", e);
        }
    }

    private static void validateTeachingAssignments(byte[] professorCsv, byte[] sectionCsv) {
        List<String> roster = emails(professorCsv);
        var instructors = new java.util.HashSet<String>();
        try (CSVParser parser = CSVParser.parse(
                csvText(sectionCsv),
                CSVFormat.DEFAULT
                        .builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setTrim(true)
                        .get())) {
            for (var row : parser) {
                String email = row.get("instructor_email").strip().toLowerCase(Locale.ROOT);
                if (email.isEmpty()) continue;
                if (!roster.contains(email))
                    throw new IllegalArgumentException("Section instructor " + email + " is not in the professor CSV.");
                instructors.add(email);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read section instructors.", e);
        }
        for (String email : roster) {
            if (!instructors.contains(email))
                throw new IllegalArgumentException("Professor " + email + " has no section in the section CSV.");
        }
    }

    private static String csvText(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }
}
