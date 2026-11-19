package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.service.AcDashboardService;
import edu.utdallas.cs4485.assessmenthelper.service.CycleDashboardSnapshotService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('AC')")
@RequestMapping("/api/ac-dashboard")
public class AcDashboardController {
    private final AcDashboardService service;
    private final CycleDashboardSnapshotService snapshots;

    public AcDashboardController(AcDashboardService service, CycleDashboardSnapshotService snapshots) {
        this.service = service;
        this.snapshots = snapshots;
    }

    @GetMapping("/metrics")
    public AcDashboardService.Metrics metrics() {
        return service.metrics();
    }

    @GetMapping("/cycles/{cycleId}/metrics")
    public AcDashboardService.Metrics metricsForCycle(@PathVariable int cycleId) {
        return snapshots.get(cycleId).metrics();
    }

    @GetMapping("/observations")
    public AcDashboardService.Observations observations() {
        return service.observations();
    }

    @GetMapping("/cycles/{cycleId}/observations")
    public AcDashboardService.Observations observationsForCycle(@PathVariable int cycleId) {
        return snapshots.get(cycleId).observations();
    }

    @PostMapping("/observations/reminders")
    public AcDashboardService.Reminders remind() {
        return service.remind();
    }
}
