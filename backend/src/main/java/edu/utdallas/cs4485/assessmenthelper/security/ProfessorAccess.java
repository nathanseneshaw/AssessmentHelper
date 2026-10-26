package edu.utdallas.cs4485.assessmenthelper.security;

import edu.utdallas.cs4485.assessmenthelper.repository.DepartmentRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRequestRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import org.springframework.stereotype.Component;

@Component
public class ProfessorAccess {

    private final ProfessorRepository professorRepository;
    private final DepartmentRepository departmentRepository;
    private final ObserverRequestRepository observerRequestRepository;
    private final ObserverRepository observerRepository;

    public ProfessorAccess(
            ProfessorRepository professorRepository,
            DepartmentRepository departmentRepository,
            ObserverRequestRepository observerRequestRepository,
            ObserverRepository observerRepository) {
        this.professorRepository = professorRepository;
        this.departmentRepository = departmentRepository;
        this.observerRequestRepository = observerRequestRepository;
        this.observerRepository = observerRepository;
    }

    public boolean isSelf(AccountPrincipal principal, Integer professorId) {
        return professorId.equals(principal.user().professorId());
    }

    /** True when the observer request was sent to the signed-in professor, so only they may answer it. */
    public boolean isRequestedObserver(AccountPrincipal principal, Integer requestId) {
        Integer professorId = principal.user().professorId();
        return professorId != null
                && observerRequestRepository
                        .findById(requestId)
                        .flatMap(request -> observerRepository.findById(request.observerId()))
                        .map(observer -> professorId.equals(observer.professorId()))
                        .orElse(false);
    }

    public boolean canView(AccountPrincipal principal, Integer professorId) {
        CurrentUser user = principal.user();
        if (isSelf(principal, professorId)) {
            return true;
        }
        return principal.isCommitteeMember()
                && professorRepository
                        .findById(professorId)
                        .flatMap(professor -> departmentRepository.findById(professor.departmentId()))
                        .map(department -> department.schoolId().equals(user.schoolId()))
                        .orElse(false);
    }
}
