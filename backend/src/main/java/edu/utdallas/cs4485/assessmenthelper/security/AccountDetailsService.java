package edu.utdallas.cs4485.assessmenthelper.security;

import edu.utdallas.cs4485.assessmenthelper.entity.Department;
import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.entity.UserAccount;
import edu.utdallas.cs4485.assessmenthelper.repository.CommitteeMemberRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.DepartmentRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.UserAccountRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountDetailsService implements UserDetailsService {

    private final UserAccountRepository accountRepository;
    private final ProfessorRepository professorRepository;
    private final DepartmentRepository departmentRepository;
    private final CommitteeMemberRepository committeeMemberRepository;
    private final Clock clock;

    public AccountDetailsService(
            UserAccountRepository accountRepository,
            ProfessorRepository professorRepository,
            DepartmentRepository departmentRepository,
            CommitteeMemberRepository committeeMemberRepository,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.professorRepository = professorRepository;
        this.departmentRepository = departmentRepository;
        this.committeeMemberRepository = committeeMemberRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public AccountPrincipal loadUserByUsername(String email) {
        UserAccount account = accountRepository
                .findByEmailIgnoreCase(email.trim())
                .filter(found -> found.professorId() != null)
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));
        Professor professor = professorRepository
                .findById(account.professorId())
                .orElseThrow(() -> new UsernameNotFoundException("No professor for " + email));

        Integer schoolId = departmentRepository
                .findById(professor.departmentId())
                .map(Department::schoolId)
                .orElse(null);
        List<Role> roles =
                isCommitteeMember(professor.professorId()) ? List.of(Role.AC, Role.PROFESSOR) : List.of(Role.PROFESSOR);
        CurrentUser user = new CurrentUser(
                account.userId(),
                professor.professorId(),
                schoolId,
                professor.firstName() + " " + professor.lastName(),
                account.email(),
                roles);
        return new AccountPrincipal(user, account.passwordHash(), Boolean.TRUE.equals(account.isActive()));
    }

    private boolean isCommitteeMember(Integer professorId) {
        LocalDate today = LocalDate.now(clock);
        return committeeMemberRepository
                .findByProfessorId(professorId)
                .filter(member -> Boolean.TRUE.equals(member.activeStatus()))
                .filter(member -> !member.startDate().isAfter(today))
                .filter(member -> member.endDate() == null || !member.endDate().isBefore(today))
                .isPresent();
    }
}
