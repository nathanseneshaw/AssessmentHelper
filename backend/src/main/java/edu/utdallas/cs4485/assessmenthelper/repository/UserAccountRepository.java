package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.UserAccount;
import org.springframework.data.repository.ListCrudRepository;

public interface UserAccountRepository extends ListCrudRepository<UserAccount, Integer> {}
