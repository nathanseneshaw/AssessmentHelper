package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.entity.MessageTemplate;
import edu.utdallas.cs4485.assessmenthelper.service.EmailTemplateService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/email-templates")
@PreAuthorize("hasRole('AC')")
public class EmailTemplateController {
    private final EmailTemplateService service;

    public EmailTemplateController(EmailTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public EmailTemplateService.Catalog list() {
        return service.catalog();
    }

    @PutMapping("/{type}")
    public MessageTemplate save(@PathVariable String type, @RequestBody EmailTemplateService.Update update) {
        return service.save(type, update);
    }

    @PostMapping("/preview")
    public EmailTemplateService.Preview preview(@RequestBody EmailTemplateService.Update update) {
        return service.preview(update);
    }
}
