package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.utdallas.cs4485.assessmenthelper.entity.Course;
import edu.utdallas.cs4485.assessmenthelper.entity.CourseSection;
import edu.utdallas.cs4485.assessmenthelper.entity.Department;
import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationCycle;
import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationSignup;
import edu.utdallas.cs4485.assessmenthelper.entity.Observee;
import edu.utdallas.cs4485.assessmenthelper.entity.Observer;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverMatch;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverRequest;
import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.entity.School;
import edu.utdallas.cs4485.assessmenthelper.entity.Semester;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRequestContextRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

@DataJdbcTest
@Import({
    ObserverRequestService.class,
    ObserverEligibilityService.class,
    ObserverRequestContextRepository.class,
    ObserverRequestServiceTests.FixedClock.class
})
class ObserverRequestServiceTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 12, 0);

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        }
    }

    @Autowired
    private ObserverRequestService service;

    @Autowired
    private JdbcAggregateTemplate db;

    private EvaluationSignup signup;
    private Observer observer;

    @BeforeEach
    void createSignupAndObserver() {
        School school = db.insert(new School(null, "Test School"));
        Department department = db.insert(new Department(null, school.schoolId(), "Test Department", "TEST"));
        Course course = db.insert(new Course(null, department.departmentId(), "T", "1000", "C", "UNDERGRADUATE"));
        Semester semester = db.insert(new Semester(null, "Fall", 2026, null, null));
        EvaluationCycle cycle = db.insert(new EvaluationCycle(null, semester.semesterId(), null, null, null, "OPEN"));
        Professor observee = professor(department, "observee@test.edu");
        Professor observerProfessor = professor(department, "observer@test.edu");
        Observee observeeRow = db.insert(new Observee(null, observee.professorId(), cycle.cycleId(), "ACTIVE"));
        observer = db.insert(new Observer(null, observerProfessor.professorId(), cycle.cycleId(), true));
        CourseSection section = db.insert(
                new CourseSection(null, course.courseId(), semester.semesterId(), "001", null, null, null, null));
        signup = db.insert(new EvaluationSignup(null, observeeRow.observeeId(), section.sectionId(), null, "PENDING"));
        db.insert(new ObserverMatch(null, signup.signupId(), observer.observerId(), NOW.toLocalDate(), "PROPOSED"));
    }

    @Test
    void createStartsPendingWithDefaultWeekLongWindow() {
        ObserverRequest request = service.create(signup.signupId(), observer.observerId(), null);

        assertThat(request.requestId()).isNotNull();
        assertThat(request.status()).isEqualTo("PENDING");
        assertThat(request.requestDate()).isEqualTo(NOW);
        assertThat(request.expirationDate()).isEqualTo(NOW.plusDays(7));
        assertThat(request.responseDate()).isNull();
    }

    @Test
    void createUsesGivenExpirationDate() {
        ObserverRequest request = service.create(signup.signupId(), observer.observerId(), NOW.plusDays(2));

        assertThat(request.expirationDate()).isEqualTo(NOW.plusDays(2));
    }

    @Test
    void createRejectsPastExpirationAndMissingIds() {
        assertThatThrownBy(() -> service.create(signup.signupId(), observer.observerId(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(null, observer.observerId(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsUnknownSignupOrObserver() {
        assertThatThrownBy(() -> service.create(-1, observer.observerId(), null))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No evaluation signup with id -1");
        assertThatThrownBy(() -> service.create(signup.signupId(), -1, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No observer with id -1");
    }

    @Test
    void confirmBeforeExpirationSetsConfirmedAndResponseDate() {
        ObserverRequest pending = service.create(signup.signupId(), observer.observerId(), null);

        ObserverRequest confirmed = service.confirm(pending.requestId());

        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
        assertThat(confirmed.responseDate()).isEqualTo(NOW);
        assertThat(service.findById(pending.requestId())).isEqualTo(confirmed);
    }

    @Test
    void confirmingTwiceIsAConflict() {
        ObserverRequest pending = service.create(signup.signupId(), observer.observerId(), null);
        service.confirm(pending.requestId());

        assertThatThrownBy(() -> service.confirm(pending.requestId()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Request " + pending.requestId() + " is already CONFIRMED");
    }

    @Test
    void overdueRequestReadsAsExpiredAndCannotBeConfirmed() {
        ObserverRequest overdue = insertRequest("PENDING", NOW.minusMinutes(1));

        assertThat(service.findById(overdue.requestId()).status()).isEqualTo("EXPIRED");
        assertThatThrownBy(() -> service.confirm(overdue.requestId()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Request " + overdue.requestId() + " has expired");
    }

    @Test
    void requestExpiringExactlyNowIsStillPending() {
        ObserverRequest request = insertRequest("PENDING", NOW);

        assertThat(service.findById(request.requestId()).status()).isEqualTo("PENDING");
    }

    @Test
    void confirmedRequestNeverExpires() {
        ObserverRequest confirmed = insertRequest("CONFIRMED", NOW.minusDays(30));

        assertThat(service.findById(confirmed.requestId()).status()).isEqualTo("CONFIRMED");
        assertThat(service.expireOverdue()).isZero();
    }

    @Test
    void expireOverdueSweepsOnlyOverduePendingRequests() {
        ObserverRequest overdue = insertRequest("PENDING", NOW.minusDays(1));
        ObserverRequest active = insertRequest("PENDING", NOW.plusDays(1));

        assertThat(service.expireOverdue()).isEqualTo(1);
        assertThat(service.findById(overdue.requestId()).status()).isEqualTo("EXPIRED");
        assertThat(service.findById(active.requestId()).status()).isEqualTo("PENDING");
        assertThat(service.expireOverdue()).isZero();
    }

    @Test
    void findFiltersBySignupAndObserver() {
        ObserverRequest request = service.create(signup.signupId(), observer.observerId(), null);

        assertThat(service.find(signup.signupId(), null)).containsExactly(request);
        assertThat(service.find(null, observer.observerId())).containsExactly(request);
        assertThat(service.find(-1, null)).isEmpty();
    }

    @Test
    void unknownRequestThrowsNotFound() {
        assertThatThrownBy(() -> service.findById(-1))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No observer request with id -1");
        assertThatThrownBy(() -> service.confirm(-1)).isInstanceOf(NotFoundException.class);
    }

    private ObserverRequest insertRequest(String status, LocalDateTime expiration) {
        return db.insert(new ObserverRequest(
                null,
                signup.signupId(),
                observer.observerId(),
                NOW.minusDays(8),
                expiration,
                "CONFIRMED".equals(status) ? NOW.minusDays(9) : null,
                status));
    }

    private Professor professor(Department department, String email) {
        return db.insert(new Professor(null, department.departmentId(), "FULL", "T", "P", email, null, true));
    }
}
