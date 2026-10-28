package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Observation forms and the list that links to them. Who may open or change one form depends on its status, so
 * {@link ObservationService} decides that rather than an annotation here.
 */
@RestController
@RequestMapping("/api")
public class ObservationController {
    /** Every answer on the form, for a draft save or the final submit. */
    public record ResponsesBody(List<ObservationService.Answer> responses) {}

    /** The professor an Assessment Committee member forwards a locked record's link to. */
    public record ForwardBody(Integer recipientProfessorId) {}

    private final ObservationService service;

    public ObservationController(ObservationService service) {
        this.service = service;
    }

    @GetMapping("/observations/{observationId}")
    public ObservationService.Detail detail(
            @PathVariable int observationId, @AuthenticationPrincipal AccountPrincipal principal) {
        return service.detail(observationId, principal);
    }

    @PostMapping("/observations/{observationId}/start")
    public ObservationService.Detail start(
            @PathVariable int observationId, @AuthenticationPrincipal AccountPrincipal principal) {
        return service.start(observationId, principal);
    }

    @PutMapping("/observations/{observationId}/responses")
    public ObservationService.Detail saveResponses(
            @PathVariable int observationId,
            @RequestBody ResponsesBody body,
            @AuthenticationPrincipal AccountPrincipal principal) {
        return service.saveResponses(observationId, body.responses(), principal);
    }

    @PostMapping("/observations/{observationId}/submit")
    public ObservationService.Detail submit(
            @PathVariable int observationId,
            @RequestBody ResponsesBody body,
            @AuthenticationPrincipal AccountPrincipal principal) {
        return service.submit(observationId, body.responses(), principal);
    }

    @PostMapping("/observations/{observationId}/forward")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forward(
            @PathVariable int observationId,
            @RequestBody ForwardBody body,
            @AuthenticationPrincipal AccountPrincipal principal) {
        if (body == null || body.recipientProfessorId() == null)
            throw new IllegalArgumentException("Send the professor to forward the record to.");
        service.forward(observationId, body.recipientProfessorId(), principal);
    }

    @GetMapping("/professors/{professorId}/observations")
    @PreAuthorize("@professorAccess.canView(principal, #professorId)")
    public List<ObservationService.Summary> professorObservations(@PathVariable Integer professorId) {
        return service.professorObservations(professorId);
    }
}
