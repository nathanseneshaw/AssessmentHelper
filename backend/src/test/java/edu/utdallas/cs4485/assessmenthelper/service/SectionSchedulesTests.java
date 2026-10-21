package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.assertThat;

import edu.utdallas.cs4485.assessmenthelper.entity.CourseSection;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class SectionSchedulesTests {

    private static final CourseSection TR_10_TO_1115 = section("TR", "10:00", "11:15");

    @Test
    void overlappingTimesOnASharedDayConflict() {
        CourseSection other = section("TR", "10:30", "11:45");

        assertThat(SectionSchedules.conflict(TR_10_TO_1115, other)).isTrue();
        assertThat(SectionSchedules.conflict(other, TR_10_TO_1115)).isTrue();
    }

    @Test
    void sectionInsideTheOtherConflicts() {
        assertThat(SectionSchedules.conflict(TR_10_TO_1115, section("R", "10:15", "10:45")))
                .isTrue();
        assertThat(SectionSchedules.conflict(TR_10_TO_1115, section("TR", "10:00", "11:15")))
                .isTrue();
    }

    @Test
    void oneSharedDayIsEnough() {
        assertThat(SectionSchedules.conflict(TR_10_TO_1115, section("MWRF", "11:00", "11:50")))
                .isTrue();
    }

    @Test
    void sameTimeOnDifferentDaysDoesNotConflict() {
        assertThat(SectionSchedules.conflict(TR_10_TO_1115, section("MWF", "10:00", "11:15")))
                .isFalse();
    }

    @Test
    void backToBackSectionsDoNotConflict() {
        assertThat(SectionSchedules.conflict(TR_10_TO_1115, section("TR", "11:15", "12:30")))
                .isFalse();
        assertThat(SectionSchedules.conflict(TR_10_TO_1115, section("TR", "08:45", "10:00")))
                .isFalse();
    }

    @Test
    void sectionsWithoutAFullScheduleNeverConflict() {
        assertThat(SectionSchedules.conflict(TR_10_TO_1115, section(null, "10:00", "11:15")))
                .isFalse();
        assertThat(SectionSchedules.conflict(TR_10_TO_1115, section(" ", "10:00", "11:15")))
                .isFalse();
        assertThat(SectionSchedules.conflict(TR_10_TO_1115, section("TR", null, "11:15")))
                .isFalse();
        assertThat(SectionSchedules.conflict(section("TR", "10:00", null), TR_10_TO_1115))
                .isFalse();
    }

    @Test
    void meetingDaysIgnoreCase() {
        assertThat(SectionSchedules.conflict(TR_10_TO_1115, section("tr", "10:30", "11:45")))
                .isTrue();
    }

    private static CourseSection section(String meetingDays, String start, String end) {
        return new CourseSection(
                null,
                null,
                null,
                "001",
                meetingDays,
                start == null ? null : LocalTime.parse(start),
                end == null ? null : LocalTime.parse(end),
                null);
    }
}
