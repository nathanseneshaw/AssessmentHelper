package edu.utdallas.cs4485.assessmenthelper.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class DemoTabAuthenticationFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Demo-Session";
    private static final String ATTRIBUTE_PREFIX = DemoTabAuthenticationFilter.class.getName() + ".";

    public static String start(HttpServletRequest request, AccountPrincipal principal) {
        String token = UUID.randomUUID().toString();
        var authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        request.getSession().setAttribute(ATTRIBUTE_PREFIX + token, authentication);
        return token;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = request.getHeader(HEADER);
        if (token != null) {
            var session = request.getSession(false);
            var context = SecurityContextHolder.createEmptyContext();
            if (session != null
                    && session.getAttribute(ATTRIBUTE_PREFIX + token)
                            instanceof UsernamePasswordAuthenticationToken authentication) {
                context.setAuthentication(authentication);
            }
            // An expired tab session must never fall back to the shared account.
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }
}
