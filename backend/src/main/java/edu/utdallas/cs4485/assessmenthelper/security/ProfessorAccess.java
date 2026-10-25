package edu.utdallas.cs4485.assessmenthelper.security;

import edu.utdallas.cs4485.assessmenthelper.repository.DepartmentRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import org.springframework.stereotype.Component;

@Component
public class ProfessorAccess {

    private final ProfessorRepository professorRepository;
    private final DepartmentRepository departmentRepository;

    public ProfessorAccess(ProfessorRepository professorRepository, DepartmentRepository departmentRepository) {
        this.professorRepository = professorRepository;
        this.departmentRepository = departmentRepository;
    }

    public boolean isSelf(AccountPrincipal principal, Integer professorId) {
        return professorId.equals(principal.user().professorId());
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
