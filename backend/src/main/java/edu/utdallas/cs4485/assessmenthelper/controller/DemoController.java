package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.service.DemoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
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

    private final DemoService service;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public DemoController(DemoService service) {
        this.service = service;
    }

    @GetMapping("/accounts")
    public List<DemoService.Account> accounts() {
        return service.accounts();
    }

    @PostMapping("/switch")
    public CurrentUser switchAccount(
            @RequestBody SwitchBody body, HttpServletRequest request, HttpServletResponse response) {
        if (body == null || body.userId() == null)
            throw new IllegalArgumentException("Choose an account to switch to.");
        AccountPrincipal principal = service.principal(body.userId());
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        contextHolder.setContext(context);
        if (request.getSession(false) != null) request.changeSessionId();
        contextRepository.saveContext(context, request, response);
        return principal.user();
    }
}
