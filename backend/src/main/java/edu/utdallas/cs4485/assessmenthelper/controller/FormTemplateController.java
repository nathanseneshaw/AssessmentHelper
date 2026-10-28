package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.service.FormTemplateService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/form-template")
@PreAuthorize("hasRole('AC')")
public class FormTemplateController {
    private final FormTemplateService service;

    public FormTemplateController(FormTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public FormTemplateService.Template current() {
        return service.current();
    }

    @PutMapping
    public FormTemplateService.Template save(
            @RequestBody FormTemplateService.Update update, @AuthenticationPrincipal AccountPrincipal principal) {
        return service.save(update, principal.user().userId());
    }

    public record RestoreRequest(Integer templateId) {}

    @GetMapping("/versions")
    public java.util.List<FormTemplateService.Template> history() {
        return service.history();
    }

    @PostMapping("/versions/{sourceId}/restore")
    public FormTemplateService.Template restore(
            @PathVariable int sourceId,
            @RequestBody RestoreRequest request,
            @AuthenticationPrincipal AccountPrincipal principal) {
        return service.restore(sourceId, request.templateId());
    }
}
