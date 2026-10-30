package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationAuditService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/observations/{observationId}/audit-log")
public class ObservationAuditController {

    private final ObservationAuditService service;

    public ObservationAuditController(ObservationAuditService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('AC')")
    public List<ObservationAuditService.AuditEntry> list(
            @PathVariable int observationId, @AuthenticationPrincipal AccountPrincipal principal) {
        return service.auditLog(observationId, principal);
    }
}
