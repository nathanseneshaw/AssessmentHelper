package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.service.AcDashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('AC')")
@RequestMapping("/api/ac-dashboard")
public class AcDashboardController {
    private final AcDashboardService service;

    public AcDashboardController(AcDashboardService service) {
        this.service = service;
    }

    @GetMapping("/metrics")
    public AcDashboardService.Metrics metrics() {
        return service.metrics();
    }
}
