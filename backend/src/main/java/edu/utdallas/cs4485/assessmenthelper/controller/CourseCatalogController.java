package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.entity.Course;
import edu.utdallas.cs4485.assessmenthelper.entity.CourseSection;
import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationCycle;
import edu.utdallas.cs4485.assessmenthelper.entity.Semester;
import edu.utdallas.cs4485.assessmenthelper.repository.CourseRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.CourseSectionRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationCycleRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.SemesterRepository;
import edu.utdallas.cs4485.assessmenthelper.service.CycleReadinessService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CourseCatalogController {
    private final SemesterRepository semesters;
    private final CourseRepository courses;
    private final CourseSectionRepository sections;
    private final EvaluationCycleRepository cycles;
    private final Clock clock;
    private final CycleReadinessService readiness;

    public CourseCatalogController(
            SemesterRepository semesters,
            CourseRepository courses,
            CourseSectionRepository sections,
            EvaluationCycleRepository cycles,
            Clock clock,
            CycleReadinessService readiness) {
        this.semesters = semesters;
        this.courses = courses;
        this.sections = sections;
        this.cycles = cycles;
        this.clock = clock;
        this.readiness = readiness;
    }

    @GetMapping("/semesters")
    public List<Semester> semesters(@RequestParam(defaultValue = "false") boolean signupOpen) {
        var currentDate = LocalDate.now(clock);
        var currentSemesterIds = semesters.findAll().stream()
                .filter(s -> s.startDate() != null
                        && s.endDate() != null
                        && !currentDate.isBefore(s.startDate())
                        && !currentDate.isAfter(s.endDate()))
                .map(Semester::semesterId)
                .collect(Collectors.toSet());
        var openSemesterIds = cycles.findAll().stream()
                .filter(cycle -> "OPEN".equals(cycle.status()))
                .filter(cycle -> !signupOpen
                        || (currentSemesterIds.contains(cycle.semesterId())
                                && "READY"
                                        .equals(readiness.check(cycle.cycleId()).state())))
                .map(EvaluationCycle::semesterId)
                .collect(Collectors.toSet());
        return semesters.findAll().stream()
                .filter(semester -> !signupOpen
                        || (openSemesterIds.contains(semester.semesterId())
                                && semester.startDate() != null
                                && semester.endDate() != null
                                && !LocalDate.now(clock).isBefore(semester.startDate())
                                && !LocalDate.now(clock).isAfter(semester.endDate())))
                .sorted(Comparator.comparing(Semester::startDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @GetMapping("/courses")
    public List<Course> courses() {
        return courses.findAll();
    }

    @GetMapping("/sections")
    public List<CourseSection> sections(
            @RequestParam(required = false) Integer semesterId,
            @RequestParam(required = false) Integer courseId,
            @RequestParam(required = false) Integer professorId) {
        return sections.findFiltered(semesterId, courseId, professorId);
    }
}
