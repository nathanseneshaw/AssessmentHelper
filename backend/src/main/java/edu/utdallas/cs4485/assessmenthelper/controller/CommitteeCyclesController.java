package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.entity.Semester;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationCycleRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.SemesterRepository;
import edu.utdallas.cs4485.assessmenthelper.service.CycleReadinessService;
import edu.utdallas.cs4485.assessmenthelper.service.CycleSetupService;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Cycle catalogue for the assessment committee; classification uses semester dates, not deadlines. */
@RestController
@RequestMapping("/api/ac/cycles")
@PreAuthorize("hasRole('AC')")
public class CommitteeCyclesController {
    private final EvaluationCycleRepository cycles;
    private final SemesterRepository semesters;
    private final Clock clock;
    private final CycleSetupService setup;
    private final CycleReadinessService readiness;

    public CommitteeCyclesController(
            EvaluationCycleRepository cycles,
            SemesterRepository semesters,
            Clock clock,
            CycleSetupService setup,
            CycleReadinessService readiness) {
        this.cycles = cycles;
        this.semesters = semesters;
        this.clock = clock;
        this.setup = setup;
        this.readiness = readiness;
    }

    public record CycleOption(
            Integer cycleId,
            Integer semesterId,
            String term,
            Integer year,
            LocalDate startDate,
            LocalDate endDate,
            String period,
            LocalDate signupDeadline,
            LocalDate observationDeadline,
            LocalDate feedbackDeadline) {}

    @GetMapping
    public List<CycleOption> list() {
        LocalDate today = LocalDate.now(clock);
        Map<Integer, Semester> byId =
                semesters.findAll().stream().collect(Collectors.toMap(Semester::semesterId, Function.identity()));
        return cycles.findAll().stream()
                .map(cycle -> {
                    Semester semester = byId.get(cycle.semesterId());
                    if (semester == null || semester.startDate() == null || semester.endDate() == null) return null;
                    String period = today.isBefore(semester.startDate())
                            ? "UPCOMING"
                            : today.isAfter(semester.endDate()) ? "PAST" : "CURRENT";
                    // Older seeded cycles support faculty history, but only Spring 2026 is supplied as
                    // an initial historical committee dashboard. Newly managed cycles are retained.
                    if ("PAST".equals(period)
                            && !("Spring".equalsIgnoreCase(semester.term()) && semester.academicYear() == 2026)
                            && !("Fall".equalsIgnoreCase(semester.term()) && semester.academicYear() == 2026)
                            && cycles.findRosterProfessorIds(cycle.cycleId()).isEmpty()) return null;
                    if ("CURRENT".equals(period)
                            && "FAILED".equals(readiness.check(cycle.cycleId()).state())) period = "UNAVAILABLE";
                    return new CycleOption(
                            cycle.cycleId(),
                            semester.semesterId(),
                            semester.term(),
                            semester.academicYear(),
                            semester.startDate(),
                            semester.endDate(),
                            period,
                            cycle.signupDeadline(),
                            cycle.observationDeadline(),
                            cycle.feedbackDeadline());
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(CycleOption::startDate).reversed())
                .toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public edu.utdallas.cs4485.assessmenthelper.entity.EvaluationCycle create(
            @RequestPart("setup") CycleSetupService.Setup details,
            @RequestPart("professors") MultipartFile professors,
            @RequestPart("sections") MultipartFile sections)
            throws IOException {
        return setup.create(details, professors.getBytes(), sections.getBytes());
    }

    @GetMapping("/upcoming-semesters")
    public List<CycleSetupService.SemesterOption> upcomingSemesters() {
        return setup.upcomingSemesters();
    }

    @PutMapping("/{cycleId}/dates")
    public edu.utdallas.cs4485.assessmenthelper.entity.EvaluationCycle updateDates(
            @PathVariable int cycleId, @RequestBody CycleSetupService.Setup details) {
        return setup.updateDates(cycleId, details);
    }

    @PutMapping(path = "/{cycleId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public edu.utdallas.cs4485.assessmenthelper.entity.EvaluationCycle replaceFiles(
            @PathVariable int cycleId,
            @RequestPart("professors") MultipartFile professors,
            @RequestPart("sections") MultipartFile sections)
            throws IOException {
        return setup.replaceFiles(cycleId, professors.getBytes(), sections.getBytes());
    }

    @GetMapping("/{cycleId}/readiness")
    public CycleReadinessService.Readiness readiness(@PathVariable int cycleId) {
        return readiness.check(cycleId);
    }
}
