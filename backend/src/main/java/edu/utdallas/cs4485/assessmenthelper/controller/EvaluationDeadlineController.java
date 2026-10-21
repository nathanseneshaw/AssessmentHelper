package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.service.EvaluationDeadline;
import edu.utdallas.cs4485.assessmenthelper.service.EvaluationDeadlineService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/professors/{professorId}/evaluation-deadline")
public class EvaluationDeadlineController {

    private final EvaluationDeadlineService service;

    public EvaluationDeadlineController(EvaluationDeadlineService service) {
        this.service = service;
    }

    @GetMapping
    public EvaluationDeadline get(@PathVariable Integer professorId) {
        return service.findByProfessor(professorId);
    }
}
