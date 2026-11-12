package edu.utdallas.cs4485.assessmenthelper.security;

import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRequestRepository;
import org.springframework.stereotype.Component;

@Component
public class ProfessorAccess {

    private final ObserverRequestRepository observerRequestRepository;
    private final ObserverRepository observerRepository;

    public ProfessorAccess(ObserverRequestRepository observerRequestRepository, ObserverRepository observerRepository) {
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
        return isSelf(principal, professorId) || principal.isCommitteeMember();
    }
}
