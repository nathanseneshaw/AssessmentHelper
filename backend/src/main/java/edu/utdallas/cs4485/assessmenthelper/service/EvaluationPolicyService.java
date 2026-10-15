package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationPolicy;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationPolicyRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EvaluationPolicyService {

    private final EvaluationPolicyRepository repository;

    public EvaluationPolicyService(EvaluationPolicyRepository repository) {
        this.repository = repository;
    }

    public List<EvaluationPolicy> findAll() {
        return repository.findAll();
    }

    public EvaluationPolicy findByRank(String professorRank) {
        return repository
                .findById(professorRank)
                .orElseThrow(() -> new NotFoundException("No evaluation policy for rank " + professorRank));
    }
}
