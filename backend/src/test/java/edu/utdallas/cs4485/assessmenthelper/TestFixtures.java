package edu.utdallas.cs4485.assessmenthelper;

import static org.assertj.core.api.Assertions.assertThat;

import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationCycle;
import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationSignup;
import edu.utdallas.cs4485.assessmenthelper.entity.Observation;
import edu.utdallas.cs4485.assessmenthelper.entity.ObservationTemplate;
import edu.utdallas.cs4485.assessmenthelper.entity.Observee;
import edu.utdallas.cs4485.assessmenthelper.entity.Observer;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverListState;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverMatch;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverRequest;
import edu.utdallas.cs4485.assessmenthelper.entity.ProcessFeedback;
import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.entity.Semester;
import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

public final class TestFixtures {
    // signupOf, observerOf and matchOf look only at data.sql's Fall 2026 cycle.
    private static final int FALL_2026_CYCLE = 1;

    private TestFixtures() {}

    public static <T> T only(Iterable<T> rows) {
        return assertThat(rows).singleElement().actual();
    }

    public static <T> T only(Iterable<T> rows, Predicate<? super T> filter) {
        return assertThat(rows).filteredOn(filter).singleElement().actual();
    }

    public static Professor professor(JdbcAggregateTemplate db, String email) {
        return only(db.findAll(Professor.class), p -> p.email().equals(email));
    }

    public static AccountPrincipal principal(JdbcAggregateTemplate db, String email, int schoolId, Role... roles) {
        Professor professor = professor(db, email);
        return new AccountPrincipal(
                new CurrentUser(
                        null,
                        professor.professorId(),
                        schoolId,
                        professor.firstName() + " " + professor.lastName(),
                        email,
                        List.of(roles)),
                null,
                true);
    }

    public static int signupOf(JdbcAggregateTemplate db, int professorId) {
        return only(signupsOf(db, professorId, FALL_2026_CYCLE));
    }

    public static int observerOf(JdbcAggregateTemplate db, int professorId) {
        return only(db.findAll(Observer.class), o -> o.professorId() == professorId && o.cycleId() == FALL_2026_CYCLE)
                .observerId();
    }

    public static int matchOf(JdbcAggregateTemplate db, int signupId, int observerProfessorId) {
        int observer = observerOf(db, observerProfessorId);
        return only(db.findAll(ObserverMatch.class), m -> m.signupId() == signupId && m.observerId() == observer)
                .matchId();
    }

    public static int cycleOf(JdbcAggregateTemplate db, String term, int year) {
        int semester = only(db.findAll(Semester.class), s -> s.term().equals(term) && s.academicYear() == year)
                .semesterId();
        return only(db.findAll(EvaluationCycle.class), c -> c.semesterId() == semester)
                .cycleId();
    }

    public static int observationOf(JdbcAggregateTemplate db, String observeeEmail, String term, int year) {
        var signups = signupsOf(db, professor(db, observeeEmail).professorId(), cycleOf(db, term, year));
        return only(db.findAll(Observation.class), o -> signups.contains(o.signupId()))
                .observationId();
    }

    public static int firstTemplateId(JdbcAggregateTemplate db) {
        return db.findAll(ObservationTemplate.class).stream()
                .mapToInt(ObservationTemplate::templateId)
                .min()
                .orElseThrow();
    }

    private static List<Integer> signupsOf(JdbcAggregateTemplate db, int professorId, int cycleId) {
        var observees = db.findAll(Observee.class).stream()
                .filter(o -> o.professorId() == professorId && o.cycleId() == cycleId)
                .map(Observee::observeeId)
                .toList();
        return db.findAll(EvaluationSignup.class).stream()
                .filter(s -> observees.contains(s.observeeId()))
                .map(EvaluationSignup::signupId)
                .toList();
    }

    public static Observation newObservation(int signupId, int observerId, int templateId, String status) {
        return new Observation(
                null, signupId, observerId, templateId, null, null, status, false, false, null, null, null, null);
    }

    public static Observation withStatus(Observation o, String status) {
        return new Observation(
                o.observationId(),
                o.signupId(),
                o.observerId(),
                o.templateId(),
                o.observationDate(),
                o.startTime(),
                status,
                o.observeeSigned(),
                o.observerSigned(),
                o.observeeSignedAt(),
                o.observerSignedAt(),
                o.completedAt(),
                o.observeeComments());
    }

    public static Observation withPlan(Observation o, LocalDate observationDate, LocalTime startTime) {
        return new Observation(
                o.observationId(),
                o.signupId(),
                o.observerId(),
                o.templateId(),
                observationDate,
                startTime,
                o.status(),
                o.observeeSigned(),
                o.observerSigned(),
                o.observeeSignedAt(),
                o.observerSignedAt(),
                o.completedAt(),
                o.observeeComments());
    }

    public static Observation submitted(Observation o, LocalDateTime observerSignedAt) {
        return new Observation(
                o.observationId(),
                o.signupId(),
                o.observerId(),
                o.templateId(),
                o.observationDate(),
                o.startTime(),
                "SUBMITTED",
                o.observeeSigned(),
                true,
                o.observeeSignedAt(),
                observerSignedAt,
                o.completedAt(),
                o.observeeComments());
    }

    public static Observation completed(Observation o, LocalDateTime completedAt) {
        return new Observation(
                o.observationId(),
                o.signupId(),
                o.observerId(),
                o.templateId(),
                o.observationDate(),
                o.startTime(),
                "COMPLETED",
                true,
                o.observerSigned(),
                o.observeeSignedAt(),
                o.observerSignedAt(),
                completedAt,
                o.observeeComments());
    }

    public static EvaluationSignup withStatus(EvaluationSignup s, String status) {
        return new EvaluationSignup(s.signupId(), s.observeeId(), s.sectionId(), s.signupDate(), status);
    }

    public static EvaluationCycle withSignupDeadline(EvaluationCycle c, LocalDate deadline) {
        return new EvaluationCycle(
                c.cycleId(), c.semesterId(), deadline, c.observationDeadline(), c.feedbackDeadline(), c.status());
    }

    public static EvaluationCycle withObservationDeadline(EvaluationCycle c, LocalDate deadline) {
        return new EvaluationCycle(
                c.cycleId(), c.semesterId(), c.signupDeadline(), deadline, c.feedbackDeadline(), c.status());
    }

    public static EvaluationCycle withFeedbackDeadline(EvaluationCycle c, LocalDate deadline) {
        return new EvaluationCycle(
                c.cycleId(), c.semesterId(), c.signupDeadline(), c.observationDeadline(), deadline, c.status());
    }

    public static Professor inactive(Professor p) {
        return new Professor(
                p.professorId(),
                p.departmentId(),
                p.professorRank(),
                p.firstName(),
                p.lastName(),
                p.email(),
                p.hireDate(),
                false);
    }

    public static Professor withDepartment(Professor p, int departmentId) {
        return new Professor(
                p.professorId(),
                departmentId,
                p.professorRank(),
                p.firstName(),
                p.lastName(),
                p.email(),
                p.hireDate(),
                p.activeStatus());
    }

    public static Observer inactive(Observer o) {
        return new Observer(o.observerId(), o.professorId(), o.cycleId(), false);
    }

    public static ObservationTemplate inactive(ObservationTemplate t) {
        return new ObservationTemplate(
                t.templateId(), t.createdBy(), t.templateName(), t.version(), t.createdDate(), false);
    }

    public static ObserverListState withReleasedAt(ObserverListState s, LocalDateTime releasedAt) {
        return new ObserverListState(s.signupId(), s.windowStart(), s.windowEnd(), releasedAt);
    }

    public static ObserverRequest withExpiration(ObserverRequest r, LocalDateTime expirationDate) {
        return new ObserverRequest(
                r.requestId(),
                r.signupId(),
                r.observerId(),
                r.requestDate(),
                expirationDate,
                r.responseDate(),
                r.status(),
                r.assignedByAc());
    }

    public static ProcessFeedback newFeedback(
            int cycleId, int professorId, Integer signupId, Integer overallRating, LocalDate submittedDate) {
        return new ProcessFeedback(
                null,
                cycleId,
                professorId,
                signupId,
                overallRating,
                null,
                null,
                null,
                submittedDate,
                null,
                null,
                null,
                null,
                null);
    }
}
