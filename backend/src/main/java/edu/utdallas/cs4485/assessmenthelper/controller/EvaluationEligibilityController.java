package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.service.EvaluationEligibilityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evaluation-eligibility")
public class EvaluationEligibilityController {
    private final EvaluationEligibilityService service;

    public EvaluationEligibilityController(EvaluationEligibilityService service) {
        this.service = service;
    }

    @GetMapping
    public EvaluationEligibilityService.Queue list() {
        return service.findAll();
    }
}
