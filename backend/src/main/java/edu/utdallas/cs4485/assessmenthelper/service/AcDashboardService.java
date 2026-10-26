package edu.utdallas.cs4485.assessmenthelper.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The committee dashboard's counts for the current cycle. Due faculty come from
 * {@link EvaluationEligibilityService#findDue()} and the cycle's signups from {@link ObserverEligibilityService#acState()},
 * so the numbers agree with the due list and the observer matching screen.
 */
@Service
public class AcDashboardService {
    private final EvaluationEligibilityService eligibility;
    private final ObserverEligibilityService observers;
    private final JdbcTemplate db;
    private final Clock clock;

    public AcDashboardService(
            EvaluationEligibilityService eligibility,
            ObserverEligibilityService observers,
            JdbcTemplate db,
            Clock clock) {
        this.eligibility = eligibility;
        this.observers = observers;
        this.db = db;
        this.clock = clock;
    }

    public record Ratio(int count, int total) {}

    public record Metrics(
            int cycleId,
            String cycleLabel,
            int facultyDue,
            int listSize,
            Ratio participation,
            Ratio observerUtilization,
            Ratio listSufficiency,
            int missingObservations) {}

    private record ObservationRow(int signupId, String status, LocalDate date) {}

    @Transactional(readOnly = true)
    public Metrics metrics() {
        LocalDate today = LocalDate.now(clock);
        var due = eligibility.findDue();
        var ac = observers.acState();
        if (ac.cycle().cycleId() != due.cycleId())
            throw new ConflictException("The evaluation cycle changed. Refresh the dashboard.");
        var signedUp = Set.copyOf(db.queryForList("""
                SELECT oe.professor_id FROM evaluation_signup es JOIN observee oe ON oe.observee_id=es.observee_id
                WHERE oe.cycle_id=? AND es.status<>'CANCELLED'
                """, Integer.class, due.cycleId()));
        int participating = (int) due.faculty().stream()
                .filter(f -> signedUp.contains(f.professorId()))
                .count();
        var signupIds = ac.signups().stream()
                .map(ObserverEligibilityService.Signup::signupId)
                .collect(Collectors.toSet());
        // observation.signup_id is unique, so each row is a different observed signup.
        var observations = db
                .query(
                        """
                SELECT o.signup_id, o.status, o.observation_date FROM observation o
                JOIN evaluation_signup es ON es.signup_id=o.signup_id JOIN observee oe ON oe.observee_id=es.observee_id
                WHERE oe.cycle_id=?
                """,
                        (r, n) -> new ObservationRow(r.getInt(1), r.getString(2), r.getObject(3, LocalDate.class)),
                        due.cycleId())
                .stream()
                .filter(o -> signupIds.contains(o.signupId()))
                .toList();
        int missing = (int) observations.stream()
                .filter(o -> !"COMPLETED".equalsIgnoreCase(o.status())
                        && o.date() != null
                        && o.date().isBefore(today))
                .count();
        int fullLists = (int) ac.signups().stream()
                .filter(s -> s.candidates().size() >= ObserverEligibilityService.LIST_SIZE)
                .count();
        int signups = ac.signups().size();
        return new Metrics(
                due.cycleId(),
                due.cycleLabel(),
                due.count(),
                ObserverEligibilityService.LIST_SIZE,
                new Ratio(participating, due.count()),
                new Ratio(observations.size(), signups),
                new Ratio(fullLists, signups),
                missing);
    }
}
