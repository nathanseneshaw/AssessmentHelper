package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationSignup;
import edu.utdallas.cs4485.assessmenthelper.service.EvaluationSignupService;
import edu.utdallas.cs4485.assessmenthelper.service.ObserverEligibilityService;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/professors/{professorId}/evaluation-signups/{semesterId}")
public class EvaluationSignupController {
    public record Selection(Integer sectionId, LocalDate windowEnd) {}

    private final EvaluationSignupService service;

    public EvaluationSignupController(EvaluationSignupService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@professorAccess.canView(principal, #professorId)")
    public ResponseEntity<EvaluationSignup> get(@PathVariable Integer professorId, @PathVariable Integer semesterId) {
        var signup = service.find(professorId, semesterId);
        return signup == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(signup);
    }

    @GetMapping("/window")
    @PreAuthorize("@professorAccess.canView(principal, #professorId)")
    public ObserverEligibilityService.Window window(
            @PathVariable Integer professorId, @PathVariable Integer semesterId) {
        return service.window(professorId, semesterId);
    }

    @PutMapping
    @PreAuthorize("@professorAccess.isSelf(principal, #professorId)")
    public EvaluationSignup save(
            @PathVariable Integer professorId, @PathVariable Integer semesterId, @RequestBody Selection selection) {
        return service.save(professorId, semesterId, selection.sectionId(), selection.windowEnd());
    }
}
