package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.service.ObserverEligibilityService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Generated observer lists, committee release and assignment, and observer responses. Signups are saved through
 * {@link EvaluationSignupController} and requests sent through {@link FacultyObserverRequestController}.
 */
@RestController
@RequestMapping("/api/observer-eligibility")
public class ObserverEligibilityController {
    private final ObserverEligibilityService service;

    public ObserverEligibilityController(ObserverEligibilityService service) {
        this.service = service;
    }

    public record ResponseBody(Boolean accept) {}

    public record AssignBody(Integer observerId) {}

    @GetMapping("/faculty/{id}")
    @PreAuthorize("@professorAccess.canView(principal, #id)")
    public ObserverEligibilityService.FacultyState state(@PathVariable int id) {
        return service.facultyState(id);
    }

    @GetMapping("/ac")
    @PreAuthorize("hasRole('AC')")
    public ObserverEligibilityService.AcState ac() {
        return service.acState();
    }

    @PostMapping("/generate")
    @PreAuthorize("hasRole('AC')")
    public ObserverEligibilityService.AcState generate() {
        return service.generate();
    }

    @PutMapping("/cycles/{cycleId}/deadlines")
    @PreAuthorize("hasRole('AC')")
    public ObserverEligibilityService.Cycle updateDeadlines(
            @PathVariable int cycleId, @RequestBody ObserverEligibilityService.Deadlines body) {
        return service.updateDeadlines(cycleId, body);
    }

    @PostMapping("/signups/{id}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('AC')")
    public void release(@PathVariable int id) {
        service.release(id);
    }

    @GetMapping("/signups/{id}/eligible")
    @PreAuthorize("hasRole('AC')")
    public List<ObserverEligibilityService.Candidate> eligible(@PathVariable int id) {
        return service.eligibleObservers(id);
    }

    @PostMapping("/signups/{id}/assign")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('AC')")
    public void assign(@PathVariable int id, @RequestBody AssignBody body) {
        if (body.observerId() == null) throw new IllegalArgumentException("Choose an observer to assign.");
        service.assign(id, body.observerId());
    }

    @PostMapping("/faculty/{id}/requests/{requestId}/respond")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@professorAccess.isSelf(principal, #id)")
    public void respond(@PathVariable int id, @PathVariable int requestId, @RequestBody ResponseBody body) {
        if (body.accept() == null) throw new IllegalArgumentException("Choose accept or decline.");
        service.respond(id, requestId, body.accept());
    }
}
