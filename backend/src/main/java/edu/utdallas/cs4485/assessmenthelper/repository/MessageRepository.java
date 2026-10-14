package edu.utdallas.cs4485.assessmenthelper.repository;

import edu.utdallas.cs4485.assessmenthelper.entity.Message;
import org.springframework.data.repository.ListCrudRepository;

public interface MessageRepository extends ListCrudRepository<Message, Integer> {}
