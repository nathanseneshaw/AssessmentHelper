package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.service.ObserverEligibilityService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Professor ids follow the existing demo identity model; real authentication is a separate integration. */
@RestController
@RequestMapping("/api/observer-eligibility")
public class ObserverEligibilityController {
    private final ObserverEligibilityService service;

    public ObserverEligibilityController(ObserverEligibilityService service) {
        this.service = service;
    }

    public record SignupBody(Integer sectionId, LocalDate windowStart, LocalDate windowEnd) {}

    public record RequestsBody(List<Integer> observerIds) {}

    public record ResponseBody(Boolean accept) {}

    @GetMapping("/faculty")
    public List<ObserverEligibilityService.Person> faculty() {
        return service.faculty();
    }

    @GetMapping("/faculty/{id}")
    public ObserverEligibilityService.FacultyState state(@PathVariable int id) {
        return service.facultyState(id);
    }

    @GetMapping("/ac")
    public ObserverEligibilityService.AcState ac() {
        return service.acState();
    }

    @PostMapping("/faculty/{id}/signups")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void signup(@PathVariable int id, @RequestBody SignupBody body) {
        if (body.sectionId() == null) throw new IllegalArgumentException("Select a course section.");
        service.signup(id, body.sectionId(), body.windowStart(), body.windowEnd());
    }

    @PostMapping("/generate")
    public ObserverEligibilityService.AcState generate() {
        return service.generate();
    }

    @PostMapping("/signups/{id}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable int id) {
        service.release(id);
    }

    @PostMapping("/faculty/{id}/signups/{signupId}/requests")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void send(@PathVariable int id, @PathVariable int signupId, @RequestBody RequestsBody body) {
        service.send(id, signupId, body.observerIds());
    }

    @PostMapping("/faculty/{id}/requests/{requestId}/respond")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void respond(@PathVariable int id, @PathVariable int requestId, @RequestBody ResponseBody body) {
        if (body.accept() == null) throw new IllegalArgumentException("Choose accept or decline.");
        service.respond(id, requestId, body.accept());
    }
}
