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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/observer-requests")
public class ObserverRequestController {

    /** {@code expirationDate} is optional; the service defaults it to one week from now. */
    public record CreateObserverRequest(Integer signupId, Integer observerId, LocalDateTime expirationDate) {}

    private final ObserverRequestService service;

    public ObserverRequestController(ObserverRequestService service) {
        this.service = service;
    }

    @GetMapping
    public List<ObserverRequest> list(
            @RequestParam(required = false) Integer signupId, @RequestParam(required = false) Integer observerId) {
        return service.find(signupId, observerId);
    }

    @GetMapping("/{requestId}")
    public ObserverRequest get(@PathVariable Integer requestId) {
        return service.findById(requestId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ObserverRequest create(@RequestBody CreateObserverRequest body) {
        return service.create(body.signupId(), body.observerId(), body.expirationDate());
    }

    @PostMapping("/{requestId}/confirm")
    public ObserverRequest confirm(@PathVariable Integer requestId) {
        return service.confirm(requestId);
    }
}
