package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationPolicy;
import edu.utdallas.cs4485.assessmenthelper.service.EvaluationPolicyService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evaluation-policies")
public class EvaluationPolicyController {

    private final EvaluationPolicyService service;

    public EvaluationPolicyController(EvaluationPolicyService service) {
        this.service = service;
    }

    @GetMapping
    public List<EvaluationPolicy> list() {
        return service.findAll();
    }

    @GetMapping("/{professorRank}")
    public EvaluationPolicy get(@PathVariable String professorRank) {
        return service.findByRank(professorRank);
    }
}
