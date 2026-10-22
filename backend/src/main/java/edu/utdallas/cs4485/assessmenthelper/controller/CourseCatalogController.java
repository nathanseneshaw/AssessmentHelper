package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.entity.Course;
import edu.utdallas.cs4485.assessmenthelper.entity.CourseSection;
import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationCycle;
import edu.utdallas.cs4485.assessmenthelper.entity.Semester;
import edu.utdallas.cs4485.assessmenthelper.repository.CourseRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.CourseSectionRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationCycleRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.SemesterRepository;
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

    public CourseCatalogController(
            SemesterRepository semesters,
            CourseRepository courses,
            CourseSectionRepository sections,
            EvaluationCycleRepository cycles) {
        this.semesters = semesters;
        this.courses = courses;
        this.sections = sections;
        this.cycles = cycles;
    }

    @GetMapping("/semesters")
    public List<Semester> semesters(@RequestParam(defaultValue = "false") boolean signupOpen) {
        var openSemesterIds = cycles.findAll().stream()
                .filter(cycle -> "OPEN".equals(cycle.status()))
                .map(EvaluationCycle::semesterId)
                .collect(Collectors.toSet());
        return semesters.findAll().stream()
                .filter(semester -> !signupOpen || openSemesterIds.contains(semester.semesterId()))
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
