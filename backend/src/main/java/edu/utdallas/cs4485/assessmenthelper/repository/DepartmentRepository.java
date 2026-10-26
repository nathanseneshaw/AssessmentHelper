package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.Department;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

public interface DepartmentRepository extends ListCrudRepository<Department, Integer> {

    Optional<Department> findByDepartmentCodeIgnoreCase(String departmentCode);
}
