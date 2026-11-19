package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationCycleRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.UserAccountRepository;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.DemoTabSessions;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBooleanProperty("app.demo.enabled")
public class DemoStateService {
    private final DemoTime time;
    private final Clock clock;
    private final ObserverRequestService requests;
    private final EvaluationCycleRepository cycles;
    private final UserAccountRepository accounts;
    private final DemoTabSessions sessions;
    private final DataSource dataSource;
    private final DataSourceScriptDatabaseInitializer initializer;

    public DemoStateService(
            DemoTime time,
            Clock clock,
            ObserverRequestService requests,
            EvaluationCycleRepository cycles,
            UserAccountRepository accounts,
            DemoTabSessions sessions,
            DataSource dataSource,
            DataSourceScriptDatabaseInitializer initializer) {
        this.time = time;
        this.clock = clock;
        this.requests = requests;
        this.cycles = cycles;
        this.accounts = accounts;
        this.sessions = sessions;
        this.dataSource = dataSource;
        this.initializer = initializer;
    }

    public record CycleSemester(String name, LocalDate startDate, LocalDate endDate) {
        boolean contains(LocalDate date) {
            return !date.isBefore(startDate) && !date.isAfter(endDate);
        }
    }

    public record DemoDate(LocalDate today, long daysAhead, List<CycleSemester> semesters, int expiredRequests) {}

    public synchronized DemoDate date() {
        return date(0);
    }

    // Every screen needs an evaluation cycle on the app's date. Past cycles are left out because the later
    // semesters' signups and observations would already exist there.
    public synchronized DemoDate moveTo(LocalDate date) {
        if (date == null) throw new IllegalArgumentException("Choose a date.");
        long days = ChronoUnit.DAYS.between(LocalDate.now(clock), date);
        boolean realDate = time.daysAhead() + days == 0;
        List<CycleSemester> allowed = semesters();
        if (!realDate && !allowed.isEmpty() && allowed.stream().noneMatch(s -> s.contains(date)))
            throw new IllegalArgumentException("Choose a date in a semester that has an evaluation cycle.");
        time.moveBy(days);
        return date(requests.expireOverdue());
    }

    private DemoDate date(int expiredRequests) {
        return new DemoDate(LocalDate.now(clock), time.daysAhead(), semesters(), expiredRequests);
    }

    private List<CycleSemester> semesters() {
        LocalDate realToday = LocalDate.now(clock).minusDays(time.daysAhead());
        return cycles.findCycleSemestersEndingOnOrAfter(realToday).stream()
                .map(s -> new CycleSemester(s.term() + " " + s.academicYear(), s.startDate(), s.endDate()))
                .toList();
    }

    /** Returns whether the given account still exists once the seed data is back. */
    public synchronized boolean reset(CurrentUser caller) {
        new ResourceDatabasePopulator(new ClassPathResource("demo-reset.sql")).execute(dataSource);
        initializer.initializeDatabase();
        time.reset();
        var activeAccounts = accounts.findAll().stream()
                .filter(account -> Boolean.TRUE.equals(account.isActive()))
                .collect(Collectors.toMap(account -> account.userId(), account -> account.email()));
        sessions.retainAccounts(activeAccounts);
        return Objects.equals(activeAccounts.get(caller.userId()), caller.email());
    }
}
