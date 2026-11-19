package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.service.CycleDashboardSnapshotService;
import edu.utdallas.cs4485.assessmenthelper.service.EvaluationEligibilityService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('AC')")
@RequestMapping("/api/evaluation-eligibility")
public class EvaluationEligibilityController {
    private final EvaluationEligibilityService service;
    private final CycleDashboardSnapshotService snapshots;

    public EvaluationEligibilityController(
            EvaluationEligibilityService service, CycleDashboardSnapshotService snapshots) {
        this.service = service;
        this.snapshots = snapshots;
    }

    @GetMapping
    public EvaluationEligibilityService.Queue list() {
        return service.findAll();
    }

    @GetMapping("/cycles/{cycleId}")
    public EvaluationEligibilityService.Queue listForCycle(@PathVariable int cycleId) {
        return snapshots.get(cycleId).all();
    }

    @GetMapping("/cycles/{cycleId}/due")
    public EvaluationEligibilityService.Queue dueForCycle(@PathVariable int cycleId) {
        return snapshots.get(cycleId).due();
    }

    @GetMapping("/cycles/{cycleId}/overdue")
    public EvaluationEligibilityService.Queue overdueForCycle(@PathVariable int cycleId) {
        return snapshots.get(cycleId).overdue();
    }
}
