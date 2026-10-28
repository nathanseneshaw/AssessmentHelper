package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.service.FacultyDashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/professors/{professorId}/dashboard")
public class FacultyDashboardController {
    private final FacultyDashboardService service;

    public FacultyDashboardController(FacultyDashboardService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@professorAccess.canView(principal, #professorId)")
    public FacultyDashboardService.Dashboard dashboard(@PathVariable Integer professorId) {
        return service.dashboard(professorId);
    }
}
