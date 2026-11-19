package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.DemoTabSessions;
import edu.utdallas.cs4485.assessmenthelper.service.DemoService;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
@ConditionalOnBooleanProperty("app.demo.enabled")
public class DemoController {
    public record SwitchBody(Integer userId) {}

    public record SwitchResult(CurrentUser user, String token) {}

    private final DemoService service;
    private final DemoTabSessions sessions;

    public DemoController(DemoService service, DemoTabSessions sessions) {
        this.service = service;
        this.sessions = sessions;
    }

    @GetMapping("/accounts")
    public List<DemoService.Account> accounts() {
        return service.accounts();
    }

    @PostMapping("/switch")
    public SwitchResult switchAccount(@RequestBody SwitchBody body) {
        if (body == null || body.userId() == null)
            throw new IllegalArgumentException("Choose an account to switch to.");
        AccountPrincipal principal = service.principal(body.userId());
        return new SwitchResult(principal.user(), sessions.start(principal));
    }
}
