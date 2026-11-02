package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.service.MissingObservationService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MissingObservationController {
    private final MissingObservationService service;

    public MissingObservationController(MissingObservationService service) {
        this.service = service;
    }

    @GetMapping("/api/professors/{professorId}/missing-observations")
    @PreAuthorize("@professorAccess.canView(principal, #professorId)")
    public List<MissingObservationService.Alert> missing(@PathVariable int professorId) {
        return service.findByProfessor(professorId);
    }
}
