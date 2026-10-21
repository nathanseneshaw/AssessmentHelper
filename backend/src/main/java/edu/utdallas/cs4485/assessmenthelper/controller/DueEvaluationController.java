package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationNotification;
import edu.utdallas.cs4485.assessmenthelper.service.EvaluationEligibilityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/due-evaluations")
public class DueEvaluationController {
    private final EvaluationEligibilityService service;

    public DueEvaluationController(EvaluationEligibilityService service) {
        this.service = service;
    }

    @GetMapping
    public EvaluationEligibilityService.Queue list() {
        return service.findDue();
    }

    @PostMapping("/{cycleId}/professors/{professorId}/notifications")
    public EvaluationNotification notifyProfessor(
            @PathVariable Integer cycleId,
            @PathVariable Integer professorId,
            @RequestBody EvaluationEligibilityService.Email email) {
        return service.notifyDueProfessor(cycleId, professorId, email);
    }
}
