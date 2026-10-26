package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.entity.CourseSection;
import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationSignup;
import edu.utdallas.cs4485.assessmenthelper.entity.Observer;
import edu.utdallas.cs4485.assessmenthelper.entity.TeachingHistory;
import edu.utdallas.cs4485.assessmenthelper.repository.CourseSectionRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationSignupRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.TeachingHistoryRepository;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides whether an observer is free to watch the class a signup is for. An observer is unavailable when a section
 * they teach in the same semester meets on a shared day at an overlapping time. Classes that only touch (one ends
 * when the other starts) do not overlap. A section with no meeting days or times can never conflict.
 */
@Service
public class ObserverAvailabilityService {

    /** UTD meeting day letters: M, T, W, R (Thursday), F, S, U (Sunday). */
    private static final String DAY_LETTERS = "MTWRFSU";

    private final EvaluationSignupRepository signupRepository;
    private final ObserverRepository observerRepository;
    private final TeachingHistoryRepository teachingHistoryRepository;
    private final CourseSectionRepository sectionRepository;

    public ObserverAvailabilityService(
            EvaluationSignupRepository signupRepository,
            ObserverRepository observerRepository,
            TeachingHistoryRepository teachingHistoryRepository,
            CourseSectionRepository sectionRepository) {
        this.signupRepository = signupRepository;
        this.observerRepository = observerRepository;
        this.teachingHistoryRepository = teachingHistoryRepository;
        this.sectionRepository = sectionRepository;
    }

    /**
     * @throws IllegalArgumentException if an id is null
     * @throws NotFoundException if the signup, observer, or the signup's section does not exist
     */
    @Transactional(readOnly = true)
    public boolean isAvailable(Integer observerId, Integer signupId) {
        return conflicts(observerId, signupId).isEmpty();
    }

    /** The sections the observer teaches that clash with the signup's class. Empty means the observer is available. */
    @Transactional(readOnly = true)
    public List<CourseSection> conflicts(Integer observerId, Integer signupId) {
        if (observerId == null || signupId == null) {
            throw new IllegalArgumentException("observerId and signupId are required");
        }
        EvaluationSignup signup = signupRepository
                .findById(signupId)
                .orElseThrow(() -> new NotFoundException("No evaluation signup with id " + signupId));
        Observer observer = observerRepository
                .findById(observerId)
                .orElseThrow(() -> new NotFoundException("No observer with id " + observerId));
        CourseSection target = sectionRepository
                .findById(signup.sectionId())
                .orElseThrow(() -> new NotFoundException("No course section with id " + signup.sectionId()));

        List<Integer> taughtSectionIds = teachingHistoryRepository.findByProfessorId(observer.professorId()).stream()
                .map(TeachingHistory::sectionId)
                .toList();
        if (taughtSectionIds.isEmpty()) {
            return List.of();
        }
        return sectionRepository.findAllById(taughtSectionIds).stream()
                .filter(section -> Objects.equals(section.semesterId(), target.semesterId()))
                .filter(section -> overlaps(section, target))
                .toList();
    }

    static boolean overlaps(CourseSection a, CourseSection b) {
        if (a.startTime() == null || a.endTime() == null || b.startTime() == null || b.endTime() == null) {
            return false;
        }
        if (Collections.disjoint(days(a.meetingDays()), days(b.meetingDays()))) {
            return false;
        }
        return a.startTime().isBefore(b.endTime()) && b.startTime().isBefore(a.endTime());
    }

    static Set<Character> days(String meetingDays) {
        Set<Character> days = new HashSet<>();
        if (meetingDays != null) {
            for (char letter : meetingDays.toUpperCase().toCharArray()) {
                if (DAY_LETTERS.indexOf(letter) >= 0) {
                    days.add(letter);
                }
            }
        }
        return days;
    }
}
