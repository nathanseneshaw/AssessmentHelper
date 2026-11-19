package edu.utdallas.cs4485.assessmenthelper.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBooleanProperty("app.demo.enabled")
public class DemoTabSessions {
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);
    private static final int MAX_SESSIONS = 4096;

    private record Session(Authentication authentication, Instant expiresAt) {}

    private final LinkedHashMap<String, Session> sessions = new LinkedHashMap<>(16, 0.75f, true);
    private final Clock clock = Clock.systemUTC();

    public synchronized void end(String token) {
        sessions.remove(token);
    }

    public synchronized String start(AccountPrincipal principal) {
        Instant now = clock.instant();
        sessions.values().removeIf(session -> !session.expiresAt().isAfter(now));
        if (sessions.size() >= MAX_SESSIONS) sessions.pollFirstEntry();

        String token = UUID.randomUUID().toString();
        var authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        sessions.put(token, new Session(authentication, now.plus(IDLE_TIMEOUT)));
        return token;
    }

    public synchronized Authentication authenticate(String token) {
        Session session = sessions.get(token);
        if (session == null) return null;

        Instant now = clock.instant();
        if (!session.expiresAt().isAfter(now)) {
            sessions.remove(token);
            return null;
        }
        sessions.put(token, new Session(session.authentication(), now.plus(IDLE_TIMEOUT)));
        return session.authentication();
    }
}
