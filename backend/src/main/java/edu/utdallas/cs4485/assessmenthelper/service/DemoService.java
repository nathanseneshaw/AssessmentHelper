package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.entity.UserAccount;
import edu.utdallas.cs4485.assessmenthelper.repository.UserAccountRepository;
import edu.utdallas.cs4485.assessmenthelper.security.AccountDetailsService;
import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnBooleanProperty("app.demo.enabled")
@Transactional
public class DemoService {
    private final UserAccountRepository accounts;
    private final AccountDetailsService accountDetails;
    private final Clock clock;

    public DemoService(UserAccountRepository accounts, AccountDetailsService accountDetails, Clock clock) {
        this.accounts = accounts;
        this.accountDetails = accountDetails;
        this.clock = clock;
    }

    public record Account(int userId, String name, String email, String department, boolean committee) {}

    @Transactional(readOnly = true)
    public List<Account> accounts() {
        return accounts.findDemoAccounts(LocalDate.now(clock)).stream()
                .map(row -> new Account(
                        row.userId(),
                        row.name(),
                        row.email(),
                        row.departmentName(),
                        Boolean.TRUE.equals(row.committee())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountPrincipal principal(int userId) {
        UserAccount account = accounts.findById(userId).orElseThrow(() -> new NotFoundException("Account not found."));
        AccountPrincipal principal;
        try {
            principal = accountDetails.loadUserByUsername(account.email());
        } catch (UsernameNotFoundException e) {
            throw new NotFoundException("Account not found.");
        }
        if (!principal.isEnabled()) throw new ConflictException("This account is inactive.");
        principal.eraseCredentials();
        return principal;
    }
}
