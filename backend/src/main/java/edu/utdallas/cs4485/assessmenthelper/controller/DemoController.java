package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.entity.Department;
import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.service.DemoEvaluationService;
import edu.utdallas.cs4485.assessmenthelper.service.DemoService;
import edu.utdallas.cs4485.assessmenthelper.service.DemoStateService;
import edu.utdallas.cs4485.assessmenthelper.service.MissingObservationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
@ConditionalOnBooleanProperty("app.demo.enabled")
public class DemoController {
    public record SwitchBody(Integer userId) {}

    public record Completed(int observationId) {}

    public record MoveDate(LocalDate date) {}

    public record Reset(boolean signedOut) {}

    private final DemoService service;
    private final DemoEvaluationService evaluations;
    private final DemoStateService state;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public DemoController(DemoService service, DemoEvaluationService evaluations, DemoStateService state) {
        this.service = service;
        this.evaluations = evaluations;
        this.state = state;
    }

    @GetMapping("/accounts")
    public List<DemoService.Account> accounts() {
        return service.accounts();
    }

    @GetMapping("/departments")
    public List<Department> departments() {
        return service.departments();
    }

    @PostMapping("/professors")
    public DemoService.Account addProfessor(@RequestBody DemoService.NewProfessor body) {
        return service.addProfessor(body);
    }

    @PostMapping("/sections")
    public DemoService.AddedSection addSection(@RequestBody DemoService.NewSection body) {
        return service.addSection(body);
    }

    @GetMapping("/evaluations")
    public List<MissingObservationService.MissingSignup> openEvaluations() {
        return evaluations.open();
    }

    @PostMapping("/evaluations/{signupId}/step")
    public DemoEvaluationService.Step step(@PathVariable int signupId) {
        return evaluations.step(signupId);
    }

    @PostMapping("/evaluations/{signupId}/complete")
    public Completed complete(@PathVariable int signupId) {
        return new Completed(evaluations.complete(signupId));
    }

    @GetMapping("/date")
    public DemoStateService.DemoDate date() {
        return state.date();
    }

    @PostMapping("/date")
    public DemoStateService.DemoDate moveDate(@RequestBody MoveDate body) {
        return state.moveTo(body == null ? null : body.date());
    }

    @PostMapping("/reset")
    public Reset reset(@AuthenticationPrincipal AccountPrincipal principal, HttpServletRequest request) {
        if (state.reset(principal.user().userId())) return new Reset(false);
        contextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return new Reset(true);
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
