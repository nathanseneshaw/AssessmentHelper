package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.entity.Course;
import edu.utdallas.cs4485.assessmenthelper.entity.CourseSection;
import edu.utdallas.cs4485.assessmenthelper.entity.Department;
import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.entity.TeachingHistory;
import edu.utdallas.cs4485.assessmenthelper.entity.UserAccount;
import edu.utdallas.cs4485.assessmenthelper.repository.CourseRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.CourseSectionRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.DepartmentRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationPolicyRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.TeachingHistoryRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.UserAccountRepository;
import edu.utdallas.cs4485.assessmenthelper.security.AccountDetailsService;
import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnBooleanProperty("app.demo.enabled")
@Transactional
public class DemoService {
    public static final String PASSWORD = "Faculty123!";

    private final UserAccountRepository accounts;
    private final AccountDetailsService accountDetails;
    private final ProfessorRepository professors;
    private final DepartmentRepository departments;
    private final EvaluationPolicyRepository policies;
    private final CourseRepository courses;
    private final CourseSectionRepository sections;
    private final TeachingHistoryRepository teaching;
    private final ObserverEligibilityService eligibility;
    private final Clock clock;
    private final PasswordEncoder passwords = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    public DemoService(
            UserAccountRepository accounts,
            AccountDetailsService accountDetails,
            ProfessorRepository professors,
            DepartmentRepository departments,
            EvaluationPolicyRepository policies,
            CourseRepository courses,
            CourseSectionRepository sections,
            TeachingHistoryRepository teaching,
            ObserverEligibilityService eligibility,
            Clock clock) {
        this.accounts = accounts;
        this.accountDetails = accountDetails;
        this.professors = professors;
        this.departments = departments;
        this.policies = policies;
        this.courses = courses;
        this.sections = sections;
        this.teaching = teaching;
        this.eligibility = eligibility;
        this.clock = clock;
    }

    public record Account(
            int userId, int professorId, String name, String email, String department, boolean committee) {}

    public record NewProfessor(
            String firstName, String lastName, Integer departmentId, String rank, LocalDate hireDate) {}

    public record NewSection(
            Integer professorId,
            Integer departmentId,
            String courseNumber,
            String courseName,
            String sectionNumber,
            String meetingDays,
            LocalTime startTime,
            LocalTime endTime,
            String location) {}

    public record AddedSection(int sectionId, String label, String semester) {}

    @Transactional(readOnly = true)
    public List<Account> accounts() {
        return accounts.findDemoAccounts(LocalDate.now(clock)).stream()
                .map(row -> new Account(
                        row.userId(),
                        row.professorId(),
                        row.name(),
                        row.email(),
                        row.departmentName(),
                        Boolean.TRUE.equals(row.committee())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountPrincipal principal(int userId) {
        UserAccount account = accounts.findById(userId).orElseThrow(() -> new NotFoundException("Account not found."));
        AccountPrincipal principal;
        try {
            principal = accountDetails.loadUserByUsername(account.email());
        } catch (UsernameNotFoundException e) {
            throw new NotFoundException("Account not found.");
        }
        if (!principal.isEnabled()) throw new ConflictException("This account is inactive.");
        principal.eraseCredentials();
        return principal;
    }

    @Transactional(readOnly = true)
    public List<Department> departments() {
        return departments.findAll().stream()
                .sorted(Comparator.comparing(Department::departmentName))
                .toList();
    }

    public Account addProfessor(NewProfessor input) {
        String first = text(input.firstName(), "a first name", 100);
        String last = text(input.lastName(), "a last name", 100);
        Department department = department(input.departmentId());
        if (input.rank() == null || !policies.existsById(input.rank()))
            throw new IllegalArgumentException("Choose a rank.");
        String email = unused(
                slug(first) + "." + slug(last),
                "@example.edu",
                e -> professors.existsByEmailIgnoreCase(e) || accounts.existsByEmailIgnoreCase(e));
        String username = unused(slug(first).charAt(0) + slug(last), "", accounts::existsByUsername);
        Professor professor = professors.save(new Professor(
                null, department.departmentId(), input.rank(), first, last, email, input.hireDate(), true));
        UserAccount account = accounts.save(
                new UserAccount(null, professor.professorId(), username, email, passwords.encode(PASSWORD), true));
        return new Account(
                account.userId(),
                professor.professorId(),
                first + " " + last,
                email,
                department.departmentName(),
                false);
    }

    public AddedSection addSection(NewSection input) {
        Professor professor = input.professorId() == null
                ? null
                : professors.findById(input.professorId()).orElse(null);
        if (professor == null || !Boolean.TRUE.equals(professor.activeStatus()))
            throw new IllegalArgumentException("Choose an active instructor.");
        Department department = department(input.departmentId());
        String number = input.courseNumber() == null ? "" : input.courseNumber().strip();
        if (!number.matches("[1-9][0-9]{3}"))
            throw new IllegalArgumentException("Enter a four-digit course number, such as 4485.");
        String sectionNumber = text(input.sectionNumber(), "a section number", 20);
        String days = text(input.meetingDays(), "the meeting days", 20).toUpperCase(Locale.ROOT);
        if (ObserverEligibilityService.days(days).isEmpty())
            throw new IllegalArgumentException("Enter meeting days such as MW or TR.");
        if (input.startTime() == null
                || input.endTime() == null
                || !input.startTime().isBefore(input.endTime()))
            throw new IllegalArgumentException("Enter a start time before the end time.");
        String location = input.location() == null || input.location().isBlank()
                ? null
                : text(input.location(), "a location", 100);
        Course course = courses.findFirstByDepartmentIdAndCourseNumber(department.departmentId(), number)
                .orElseGet(() -> courses.save(new Course(
                        null,
                        department.departmentId(),
                        courses.findFirstByDepartmentIdOrderByCourseId(department.departmentId())
                                .map(Course::courseCode)
                                .orElse(department.departmentCode()),
                        number,
                        text(input.courseName(), "a name for the new course", 200),
                        Integer.parseInt(number) >= 5000 ? "GRADUATE" : "UNDERGRADUATE")));
        ObserverEligibilityService.Cycle cycle = eligibility.cycle();
        CourseSection section = sections.save(new CourseSection(
                null,
                course.courseId(),
                cycle.semesterId(),
                sectionNumber,
                days,
                input.startTime(),
                input.endTime(),
                location));
        teaching.save(new TeachingHistory(null, professor.professorId(), section.sectionId()));
        return new AddedSection(
                section.sectionId(),
                course.courseCode() + " " + course.courseNumber() + "." + sectionNumber,
                cycle.label());
    }

    private Department department(Integer departmentId) {
        if (departmentId == null) throw new IllegalArgumentException("Choose a department.");
        return departments
                .findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("Choose a department."));
    }

    private static String text(String value, String what, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Enter " + what + ".");
        String stripped = value.strip();
        if (stripped.length() > maxLength)
            throw new IllegalArgumentException("Keep " + what + " to " + maxLength + " characters.");
        return stripped;
    }

    private static String slug(String name) {
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return slug.isEmpty() ? "professor" : slug;
    }

    private static String unused(String base, String suffix, Predicate<String> taken) {
        String candidate = base + suffix;
        for (int n = 2; taken.test(candidate); n++) candidate = base + n + suffix;
        return candidate;
    }
}
