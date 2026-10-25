package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.entity.Department;
import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.repository.DepartmentRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfessorService {

    private final ProfessorRepository professorRepository;
    private final DepartmentRepository departmentRepository;

    public ProfessorService(ProfessorRepository professorRepository, DepartmentRepository departmentRepository) {
        this.professorRepository = professorRepository;
        this.departmentRepository = departmentRepository;
    }

    /**
     * Returns every professor in the school with this department code, active or not, sorted by last name and then
     * first name. The code is matched ignoring case and surrounding whitespace.
     *
     * @throws NotFoundException if no school has this code. A school without professors gets an empty list instead.
     */
    @Transactional(readOnly = true)
    public List<Professor> findBySchool(String school) {
        String code = school.strip();
        Department department = departmentRepository
                .findByDepartmentCodeIgnoreCase(code)
                .orElseThrow(() -> new NotFoundException("No school with code " + code));
        return professorRepository.findByDepartmentIdOrderByLastNameAscFirstNameAsc(department.departmentId());
    }
}
