package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.entity.Semester;
import edu.utdallas.cs4485.assessmenthelper.repository.SemesterRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.UserAccountRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBooleanProperty("app.demo.enabled")
public class DemoStateService {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    private final DemoTime time;
    private final Clock clock;
    private final ObserverRequestService requests;
    private final ObserverEligibilityService eligibility;
    private final SemesterRepository semesters;
    private final UserAccountRepository accounts;
    private final DataSource dataSource;
    private final DataSourceScriptDatabaseInitializer initializer;

    public DemoStateService(
            DemoTime time,
            Clock clock,
            ObserverRequestService requests,
            ObserverEligibilityService eligibility,
            SemesterRepository semesters,
            UserAccountRepository accounts,
            DataSource dataSource,
            DataSourceScriptDatabaseInitializer initializer) {
        this.time = time;
        this.clock = clock;
        this.requests = requests;
        this.eligibility = eligibility;
        this.semesters = semesters;
        this.accounts = accounts;
        this.dataSource = dataSource;
        this.initializer = initializer;
    }

    public record DemoDate(
            LocalDate today, long daysAhead, LocalDate earliest, LocalDate latest, int expiredRequests) {}

    public DemoDate date() {
        return date(0);
    }

    // Every screen needs an evaluation cycle on the app's date, and the seed data only has an open one this semester.
    public DemoDate moveTo(LocalDate date) {
        if (date == null) throw new IllegalArgumentException("Choose a date.");
        LocalDate today = LocalDate.now(clock);
        long days = ChronoUnit.DAYS.between(today, date);
        DemoDate current = date(0);
        boolean realDate = time.daysAhead() + days == 0;
        if (!realDate
                && current.earliest() != null
                && (date.isBefore(current.earliest()) || date.isAfter(current.latest())))
            throw new IllegalArgumentException("Choose a date this semester, from " + DAY.format(current.earliest())
                    + " to " + DAY.format(current.latest()) + ".");
        time.moveBy(days);
        return date(requests.expireOverdue());
    }

    private DemoDate date(int expiredRequests) {
        Semester semester;
        try {
            semester = semesters.findById(eligibility.cycle().semesterId()).orElse(null);
        } catch (ConflictException e) {
            semester = null;
        }
        return new DemoDate(
                LocalDate.now(clock),
                time.daysAhead(),
                semester == null ? null : semester.startDate(),
                semester == null ? null : semester.endDate(),
                expiredRequests);
    }

    /** Returns whether the given account still exists once the seed data is back. */
    public boolean reset(int userId) {
        new ResourceDatabasePopulator(new ClassPathResource("demo-reset.sql")).execute(dataSource);
        initializer.initializeDatabase();
        time.reset();
        return accounts.existsById(userId);
    }
}
