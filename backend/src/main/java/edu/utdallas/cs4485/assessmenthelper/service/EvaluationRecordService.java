package edu.utdallas.cs4485.assessmenthelper.service;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;
import static java.util.Comparator.reverseOrder;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationCycle;
import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationSignup;
import edu.utdallas.cs4485.assessmenthelper.entity.Observation;
import edu.utdallas.cs4485.assessmenthelper.entity.Observee;
import edu.utdallas.cs4485.assessmenthelper.entity.Semester;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationCycleRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationSignupRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserveeRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.SemesterRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvaluationRecordService {

    private final ProfessorRepository professorRepository;
    private final ObserveeRepository observeeRepository;
    private final EvaluationCycleRepository cycleRepository;
    private final SemesterRepository semesterRepository;
    private final EvaluationSignupRepository signupRepository;
    private final ObservationRepository observationRepository;

    public EvaluationRecordService(
            ProfessorRepository professorRepository,
            ObserveeRepository observeeRepository,
            EvaluationCycleRepository cycleRepository,
            SemesterRepository semesterRepository,
            EvaluationSignupRepository signupRepository,
            ObservationRepository observationRepository) {
        this.professorRepository = professorRepository;
        this.observeeRepository = observeeRepository;
        this.cycleRepository = cycleRepository;
        this.semesterRepository = semesterRepository;
        this.signupRepository = signupRepository;
        this.observationRepository = observationRepository;
    }

    /**
     * Builds one record for each evaluation cycle in which the professor was an observee, newest semester first.
     * Semesters without a start date sort last, and ties fall back to the higher cycle id.
     *
     * <p>The status is the professor's observee status for that cycle. The observation count includes every
     * observation booked through the professor's signups in that cycle, completed or not. The completion time is
     * the latest {@code completedAt} among those observations, or null if none has been completed yet.
     *
     * <p>Impl note: The entities only hold plain foreign key ids, so the rows are loaded in batches (observees, then
     * cycles and semesters, then signups and observations) and matched up in memory. The number of queries stays the
     * same no matter how many cycles the professor has.
     *
     * @throws NotFoundException if no professor has this id. A professor who has never been observed gets an empty
     *     list instead.
     */
    @Transactional(readOnly = true)
    public List<EvaluationRecord> findByProfessor(Integer professorId) {
        List<Observee> observees = observeeRepository.findByProfessorId(professorId);
        if (observees.isEmpty()) {
            if (!professorRepository.existsById(professorId)) {
                throw new NotFoundException("No professor with id " + professorId);
            }
            return List.of();
        }

        List<EvaluationCycle> cycles = cycleRepository.findAllById(
                observees.stream().map(Observee::cycleId).toList());
        Map<Integer, Semester> semestersById =
                semesterRepository
                        .findAllById(
                                cycles.stream().map(EvaluationCycle::semesterId).toList())
                        .stream()
                        .collect(toMap(Semester::semesterId, semester -> semester));
        Map<Integer, Semester> semesterByCycleId = cycles.stream()
                .collect(toMap(EvaluationCycle::cycleId, cycle -> semestersById.get(cycle.semesterId())));

        Map<Integer, Integer> observeeIdBySignupId =
                signupRepository
                        .findByObserveeIdIn(
                                observees.stream().map(Observee::observeeId).toList())
                        .stream()
                        .collect(toMap(EvaluationSignup::signupId, EvaluationSignup::observeeId));
        Map<Integer, List<Observation>> observationsByObserveeId =
                observationRepository.findBySignupIdIn(observeeIdBySignupId.keySet()).stream()
                        .collect(groupingBy(observation -> observeeIdBySignupId.get(observation.signupId())));

        Comparator<Observee> newestFirst = comparing(
                        (Observee observee) ->
                                semesterByCycleId.get(observee.cycleId()).startDate(),
                        nullsLast(reverseOrder()))
                .thenComparing(Observee::cycleId, reverseOrder());

        return observees.stream()
                .sorted(newestFirst)
                .map(observee -> toRecord(
                        observee,
                        semesterByCycleId.get(observee.cycleId()),
                        observationsByObserveeId.getOrDefault(observee.observeeId(), List.of())))
                .toList();
    }

    private static EvaluationRecord toRecord(Observee observee, Semester semester, List<Observation> observations) {
        return new EvaluationRecord(
                observee.cycleId(),
                semester.term(),
                semester.academicYear(),
                observee.status(),
                observations.size(),
                observations.stream()
                        .map(Observation::completedAt)
                        .filter(Objects::nonNull)
                        .max(naturalOrder())
                        .orElse(null));
    }
}
