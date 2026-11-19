package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.service.CycleDashboardSnapshotService;
import edu.utdallas.cs4485.assessmenthelper.service.MissingObservationService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MissingObservationController {
    private final MissingObservationService service;
    private final CycleDashboardSnapshotService snapshots;

    public MissingObservationController(MissingObservationService service, CycleDashboardSnapshotService snapshots) {
        this.service = service;
        this.snapshots = snapshots;
    }

    @GetMapping("/api/professors/{professorId}/missing-observations")
    @PreAuthorize("@professorAccess.canView(principal, #professorId)")
    public List<MissingObservationService.Alert> missing(@PathVariable int professorId) {
        return service.findByProfessor(professorId);
    }

    /** Committee only: signups past the observation deadline with no completed observation. */
    @GetMapping("/api/missing-observations")
    @PreAuthorize("hasRole('AC')")
    public MissingObservationService.MissingSignups missingSignups() {
        return service.findMissingSignups();
    }

    @GetMapping("/api/cycles/{cycleId}/missing-observations")
    @PreAuthorize("hasRole('AC')")
    public MissingObservationService.MissingSignups missingSignupsForCycle(@PathVariable int cycleId) {
        return snapshots.get(cycleId).missing();
    }
}
