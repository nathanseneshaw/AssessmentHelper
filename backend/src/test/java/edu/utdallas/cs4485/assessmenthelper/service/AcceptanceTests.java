package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRequestContextRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Acceptance tests named after the team's test cases (TC 16 to TC 23). Same data and clock as
 * ObserverEligibilityServiceTests: data.sql's Fall 2026 demo, today is 2026-10-21 (signup open). Dario (34) teaches
 * CS 3345.005 (section 298). Hollis (9) teaches sections 284, 288 and 291, so Dario may not pick them. Dario's list
 * normally holds Leander (13), Arlo (26), Avery (31) and Keira (41); Xiomara (54) and Perrin (17) teach at the same
 * time as Dario.
 */
@DataJdbcTest
@Import({
    ObserverEligibilityService.class,
    EvaluationSignupService.class,
    ObserverRequestService.class,
    ObserverRequestContextRepository.class,
    AcceptanceTests.Time.class
})
class AcceptanceTests {
    @TestConfiguration
    static class Time {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneId.of("America/Chicago"));
        }
    }

    static final int FALL_2026 = 13, DARIO = 34, ARLO = 26;
    static final int XIOMARA = 54, PERRIN = 17;
    static final int DARIO_SECTION = 298, HOLLIS_SECTION = 284;

    @Autowired
    ObserverEligibilityService service;

    @Autowired
    EvaluationSignupService signups;

    @Autowired
    ObserverRequestService requests;

    @Autowired
    JdbcTemplate db;

    // ---------- helpers ----------

    int darioSignsUp() {
        return signups.save(DARIO, FALL_2026, DARIO_SECTION, null).signupId();
    }

    ObserverEligibilityService.Signup list(int signupId) {
        return service.acState().signups().stream()
                .filter(s -> s.signupId() == signupId)
                .findFirst()
                .orElseThrow();
    }

    List<ObserverEligibilityService.Candidate> candidates(int signupId) {
        return list(signupId).candidates();
    }

    List<Integer> candidateProfessors(int signupId) {
        return candidates(signupId).stream()
                .map(ObserverEligibilityService.Candidate::professorId)
                .toList();
    }

    /** The committee releases the list once the signup deadline has passed. */
    void releaseAfterDeadline(int signupId) {
        db.update("UPDATE evaluation_cycle SET signup_deadline='2026-10-20' WHERE cycle_id=1");
        service.release(signupId);
        db.update("UPDATE evaluation_cycle SET signup_deadline='2026-10-31' WHERE cycle_id=1");
    }

    String requestStatus(int requestId) {
        return db.queryForObject("SELECT status FROM observer_request WHERE request_id=?", String.class, requestId);
    }

    int observationCount(int signupId) {
        return db.queryForObject("SELECT COUNT(*) FROM observation WHERE signup_id=?", Integer.class, signupId);
    }

    // ---------- TC 16: signup ----------

    @Test
    void tc16_1_signupSavesTheChosenSectionAndStartsTheObserverSearch() {
        int signup = darioSignsUp();
        assertThat(list(signup).section().label()).isEqualTo("CS 3345.005");
        assertThat(candidates(signup)).isNotEmpty();
    }

    @Test
    void tc16_2_cannotSignUpForASectionTheProfessorDoesNotTeach() {
        assertThatThrownBy(() -> signups.save(DARIO, FALL_2026, HOLLIS_SECTION, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("section you teach");
    }

    @Test
    void tc16_3_cannotSubmitASignupWithoutASection() {
        assertThatThrownBy(() -> signups.save(DARIO, FALL_2026, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- TC 18: candidate list ----------

    @Test
    void tc18_listHoldsAtMostFiveDistinctEligibleObserversAndNeverTheObservee() {
        int signup = darioSignsUp();
        var eligible = service.eligibleObservers(signup).stream()
                .map(ObserverEligibilityService.Candidate::observerId)
                .toList();
        var listed = candidates(signup).stream()
                .map(ObserverEligibilityService.Candidate::observerId)
                .toList();
        assertThat(listed).hasSize(Math.min(ObserverEligibilityService.LIST_SIZE, eligible.size()));
        assertThat(eligible).containsAll(listed);
        assertThat(new HashSet<>(listed)).hasSameSizeAs(listed);
        assertThat(candidateProfessors(signup)).doesNotContain(DARIO);
    }

    @Test
    void tc18_5_wrongDepartmentOrConflictingScheduleNeverAppears() {
        db.update("UPDATE professor SET department_id = 2 WHERE professor_id = ?", ARLO);
        int signup = darioSignsUp();
        assertThat(candidateProfessors(signup)).doesNotContain(ARLO, XIOMARA, PERRIN);
    }

    @Test
    void tc18_4_and_tc19_noEligibleObserversGivesAnEmptyListThatCannotBeReleased() {
        db.update("UPDATE observer SET active_status=FALSE WHERE cycle_id=1");
        int signup = darioSignsUp();
        assertThat(candidates(signup)).isEmpty();

        db.update("UPDATE evaluation_cycle SET signup_deadline='2026-10-20' WHERE cycle_id=1");
        assertThatThrownBy(() -> service.release(signup))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("No eligible observers");
    }

    // ---------- TC 21: sending requests ----------

    @Test
    void tc21_2_onlySelectedCandidatesGetARequest() {
        int signup = darioSignsUp();
        var c = candidates(signup);
        assertThat(c.size()).isGreaterThanOrEqualTo(4);
        releaseAfterDeadline(signup);

        requests.create(signup, c.get(0).observerId(), null);
        requests.create(signup, c.get(1).observerId(), null);
        requests.create(signup, c.get(2).observerId(), null);

        var requested =
                db.queryForList("SELECT observer_id FROM observer_request WHERE signup_id=?", Integer.class, signup);
        assertThat(requested)
                .containsExactlyInAnyOrder(
                        c.get(0).observerId(), c.get(1).observerId(), c.get(2).observerId())
                .doesNotContain(c.get(3).observerId());
    }

    // ---------- TC 22: responding ----------

    @Test
    void tc22_1_acceptingLocksInTheObserver() {
        int signup = darioSignsUp();
        var observer = candidates(signup).getFirst();
        releaseAfterDeadline(signup);
        var request = requests.create(signup, observer.observerId(), null);

        var date = service.availableDates(observer.professorId(), request.requestId())
                .getFirst();
        service.respond(observer.professorId(), request.requestId(), true, date);

        assertThat(requestStatus(request.requestId())).isEqualTo("CONFIRMED");
        assertThat(observationCount(signup)).isEqualTo(1);
        assertThat(list(signup).observerName()).isEqualTo(observer.name());
    }

    @Test
    void tc22_2_decliningDoesNotLockInTheObserver() {
        int signup = darioSignsUp();
        var observer = candidates(signup).getFirst();
        releaseAfterDeadline(signup);
        var request = requests.create(signup, observer.observerId(), null);

        service.respond(observer.professorId(), request.requestId(), false);

        assertThat(requestStatus(request.requestId())).isEqualTo("DECLINED");
        assertThat(observationCount(signup)).isZero();
    }

    // ---------- TC 22-3 / 23-1: expiry ----------

    @Test
    void tc22_3_and_tc23_1_anExpiredRequestCannotBeAccepted() {
        int signup = darioSignsUp();
        var observer = candidates(signup).getFirst();
        releaseAfterDeadline(signup);
        var request = requests.create(signup, observer.observerId(), null);

        // Time passes: the request's expiration moves into the past.
        db.update(
                "UPDATE observer_request SET expiration_date=? WHERE request_id=?",
                LocalDateTime.of(2026, 10, 21, 9, 0),
                request.requestId());

        assertThatThrownBy(() -> service.respond(observer.professorId(), request.requestId(), true))
                .isInstanceOf(ConflictException.class);
        assertThat(requestStatus(request.requestId())).isEqualTo("EXPIRED");
        assertThat(observationCount(signup)).isZero();
    }

    // ---------- Known deviations from the written stories ----------
    // Remove @Disabled to see these fail. Do not commit them enabled until the team settles the requirement.

    @Disabled("US23/TC-23: stories say 48 hours, code uses 7 days (ObserverRequestService.DEFAULT_RESPONSE_WINDOW)")
    @Test
    void tc23_requestExpiresAfter48Hours() {
        int signup = darioSignsUp();
        var observer = candidates(signup).getFirst();
        releaseAfterDeadline(signup);
        var request = requests.create(signup, observer.observerId(), null);
        assertThat(request.expirationDate())
                .isEqualTo(LocalDateTime.of(2026, 10, 21, 10, 0).plusHours(48));
    }

    @Disabled("Spec on double accept: only one observer may accept a signup (see bug 2 in the #82 list)")
    @Test
    void onlyOneObserverCanAcceptASignup() {
        int signup = darioSignsUp();
        var c = candidates(signup);
        releaseAfterDeadline(signup);
        var first = requests.create(signup, c.get(0).observerId(), null);
        var second = requests.create(signup, c.get(1).observerId(), null);

        service.respond(c.get(0).professorId(), first.requestId(), true);

        assertThatThrownBy(() -> service.respond(c.get(1).professorId(), second.requestId(), true))
                .isInstanceOf(ConflictException.class);
        assertThat(observationCount(signup)).isEqualTo(1);
    }
}
