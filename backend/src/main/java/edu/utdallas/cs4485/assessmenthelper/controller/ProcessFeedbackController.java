package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.service.ProcessFeedbackService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feedback-surveys")
public class ProcessFeedbackController {
    private final ProcessFeedbackService service;

    public ProcessFeedbackController(ProcessFeedbackService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProcessFeedbackService.Survey> surveys(@AuthenticationPrincipal AccountPrincipal principal) {
        return service.surveys(principal);
    }

    @PostMapping("/{cycleId}")
    public ProcessFeedbackService.Survey submit(
            @PathVariable int cycleId,
            @RequestBody ProcessFeedbackService.Submission body,
            @AuthenticationPrincipal AccountPrincipal principal) {
        return service.submit(cycleId, body, principal);
    }
}
