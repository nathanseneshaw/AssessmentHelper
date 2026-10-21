package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.entity.CourseSection;
import java.util.Locale;

/** Schedule checks for course sections. */
final class SectionSchedules {

    private SectionSchedules() {}

    /**
     * Returns whether two sections meet at the same time: both have meeting days, a start time and an end time, they
     * share at least one meeting day, and their time ranges overlap. A section that ends exactly when the other one
     * starts does not conflict with it, and a section with an incomplete schedule never conflicts with anything.
     *
     * <p>Meeting days are one letter per day, as in {@code MWF} or {@code TR} (R is Thursday).
     */
    static boolean conflict(CourseSection a, CourseSection b) {
        return hasSchedule(a)
                && hasSchedule(b)
                && shareDay(a.meetingDays(), b.meetingDays())
                && a.startTime().isBefore(b.endTime())
                && b.startTime().isBefore(a.endTime());
    }

    private static boolean hasSchedule(CourseSection section) {
        return section.meetingDays() != null
                && !section.meetingDays().isBlank()
                && section.startTime() != null
                && section.endTime() != null;
    }

    private static boolean shareDay(String days, String otherDays) {
        String other = otherDays.toUpperCase(Locale.ROOT);
        return days.toUpperCase(Locale.ROOT)
                .chars()
                .filter(Character::isLetter)
                .anyMatch(day -> other.indexOf(day) >= 0);
    }
}
