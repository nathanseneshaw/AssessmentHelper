package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.entity.ObserverRequest;
import edu.utdallas.cs4485.assessmenthelper.service.ObserverRequestService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/professors/{professorId}/observer-requests")
public class FacultyObserverRequestController {
    public record SendRequest(Integer signupId, Integer matchId, LocalDateTime expirationDate) {}

    private final ObserverRequestService service;

    public FacultyObserverRequestController(ObserverRequestService service) {
        this.service = service;
    }

    @GetMapping
    public List<ObserverRequestService.RequestOptions> options(@PathVariable Integer professorId) {
        return service.options(professorId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ObserverRequest send(@PathVariable Integer professorId, @RequestBody SendRequest body) {
        return service.createFromMatch(professorId, body.signupId(), body.matchId(), body.expirationDate());
    }
}
