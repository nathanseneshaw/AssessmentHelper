package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.CommitteeMember;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

public interface CommitteeMemberRepository extends ListCrudRepository<CommitteeMember, Integer> {

    Optional<CommitteeMember> findByProfessorId(Integer professorId);
}
