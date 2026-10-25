package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationCycle;
import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationSignup;
import edu.utdallas.cs4485.assessmenthelper.entity.Observee;
import edu.utdallas.cs4485.assessmenthelper.repository.CourseSectionRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationCycleRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationSignupRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserveeRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRequestContextRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.SemesterRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.TeachingHistoryRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvaluationSignupService {
    private final ProfessorRepository professors;
    private final SemesterRepository semesters;
    private final EvaluationCycleRepository cycles;
    private final CourseSectionRepository sections;
    private final TeachingHistoryRepository teaching;
    private final ObserveeRepository observees;
    private final EvaluationSignupRepository signups;
    private final Clock clock;
    private final ObserverRequestContextRepository requestContext;

    public EvaluationSignupService(
            ProfessorRepository professors,
            SemesterRepository semesters,
            EvaluationCycleRepository cycles,
            CourseSectionRepository sections,
            TeachingHistoryRepository teaching,
            ObserveeRepository observees,
            EvaluationSignupRepository signups,
            Clock clock,
            ObserverRequestContextRepository requestContext) {
        this.professors = professors;
        this.semesters = semesters;
        this.cycles = cycles;
        this.sections = sections;
        this.teaching = teaching;
        this.observees = observees;
        this.signups = signups;
        this.clock = clock;
        this.requestContext = requestContext;
    }

    @Transactional(readOnly = true)
    public EvaluationSignup find(Integer professorId, Integer semesterId) {
        if (!professors.existsById(professorId)) {
            throw new NotFoundException("No professor with id " + professorId);
        }
        var observee = findObservee(professorId, openCycle(semesterId).cycleId());
        return observee == null ? null : findSignup(observee.observeeId());
    }

    @Transactional
    public EvaluationSignup save(Integer professorId, Integer semesterId, Integer sectionId) {
        if (sectionId == null || sectionId <= 0) {
            throw new IllegalArgumentException("A valid sectionId is required.");
        }
        var professor = professors
                .findForSignupUpdate(professorId)
                .orElseThrow(() -> new NotFoundException("No professor with id " + professorId));
        if (!Boolean.TRUE.equals(professor.activeStatus())) {
            throw new ConflictException("This professor is not active.");
        }
        var cycle = openCycle(semesterId);
        var section = sections.findById(sectionId)
                .orElseThrow(() -> new NotFoundException("No section with id " + sectionId));
        if (!section.semesterId().equals(semesterId)) {
            throw new IllegalArgumentException("The section does not belong to the selected semester.");
        }
        if (!teaching.existsByProfessorIdAndSectionId(professorId, sectionId)) {
            throw new IllegalArgumentException("You can only select a section you teach.");
        }
        var observee = findObservee(professorId, cycle.cycleId());
        if (observee == null) {
            observee = observees.save(new Observee(null, professorId, cycle.cycleId(), "ACTIVE"));
        }
        if (!"ACTIVE".equals(observee.status())) {
            throw new ConflictException("This evaluation is no longer accepting course selections.");
        }
        var existing = findSignup(observee.observeeId());
        if (existing != null) {
            // An identical retry is safe, including a retry after matching has started.
            if (existing.sectionId().equals(sectionId)) {
                return existing;
            }
            if (!"PENDING".equals(existing.status()) || requestContext.hasStartedMatching(existing.signupId())) {
                throw new ConflictException("This signup cannot be changed after matching has started.");
            }
            return signups.save(new EvaluationSignup(
                    existing.signupId(), existing.observeeId(), sectionId, existing.signupDate(), existing.status()));
        }
        return signups.save(
                new EvaluationSignup(null, observee.observeeId(), sectionId, LocalDate.now(clock), "PENDING"));
    }

    private EvaluationCycle openCycle(Integer semesterId) {
        if (!semesters.existsById(semesterId)) {
            throw new NotFoundException("No semester with id " + semesterId);
        }
        var open = cycles.findAll().stream()
                .filter(cycle -> cycle.semesterId().equals(semesterId) && "OPEN".equals(cycle.status()))
                .toList();
        if (open.size() != 1) {
            throw new ConflictException(
                    open.isEmpty()
                            ? "Evaluation sign-up is not open for this semester."
                            : "More than one evaluation cycle is open for this semester. Contact the committee.");
        }
        return open.getFirst();
    }

    private Observee findObservee(Integer professorId, Integer cycleId) {
        var matches = observees.findByProfessorId(professorId).stream()
                .filter(observee -> observee.cycleId().equals(cycleId))
                .toList();
        if (matches.size() > 1) {
            throw new ConflictException("Multiple evaluation records exist. Contact the committee.");
        }
        return matches.isEmpty() ? null : matches.getFirst();
    }

    private EvaluationSignup findSignup(Integer observeeId) {
        var matches = signups.findByObserveeIdIn(List.of(observeeId));
        if (matches.size() > 1) {
            throw new ConflictException("Multiple signups exist. Contact the committee.");
        }
        return matches.isEmpty() ? null : matches.getFirst();
    }
}
