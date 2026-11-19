package edu.utdallas.cs4485.assessmenthelper.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class DemoTabAuthenticationFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Demo-Session";
    private final DemoTabSessions sessions;

    public DemoTabAuthenticationFilter(DemoTabSessions sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = request.getHeader(HEADER);
        if (token != null) {
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(sessions.authenticate(token));
            // An expired tab session must never fall back to the shared account.
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }
}
