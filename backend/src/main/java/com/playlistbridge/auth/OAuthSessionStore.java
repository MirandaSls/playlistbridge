package com.playlistbridge.auth;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OAuthSessionStore {
    public static final String COOKIE_NAME = "PLAYLISTBRIDGE_SESSION";
    public static final int SESSION_MAX_AGE_SECONDS = 1800;

    private static final Duration SESSION_TTL = Duration.ofSeconds(SESSION_MAX_AGE_SECONDS);
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public Authorization begin(String existingSessionId, OAuthProvider provider) {
        removeExpiredSessions();
        String sessionId = randomToken();
        Session session = new Session();
        if (existingSessionId != null && !existingSessionId.isBlank()) {
            Session previous = sessions.remove(existingSessionId);
            if (previous != null) {
                synchronized (previous) {
                    session.tokens.putAll(previous.tokens);
                }
            }
        }
        sessions.put(sessionId, session);
        synchronized (session) {
            session.lastSeen = Instant.now();
            String state = randomToken();
            session.pendingStates.put(provider, state);
            return new Authorization(sessionId, state);
        }
    }

    public boolean consumeState(String sessionId, OAuthProvider provider, String state) {
        if (sessionId == null || sessionId.isBlank() || state == null || state.isBlank()) {
            return false;
        }
        Session session = sessions.get(sessionId);
        if (session == null) {
            return false;
        }
        synchronized (session) {
            String expectedState = session.pendingStates.remove(provider);
            session.lastSeen = Instant.now();
            return expectedState != null && secureEquals(expectedState, state);
        }
    }

    public void connect(String sessionId, OAuthProvider provider, OAuthTokenClient.TokenResponse token) {
        Session session = requireSession(sessionId);
        synchronized (session) {
            session.tokens.put(provider, token);
            session.lastSeen = Instant.now();
        }
    }

    public SessionStatus status(String sessionId) {
        removeExpiredSessions();
        Session session = sessionId == null ? null : sessions.get(sessionId);
        if (session == null) {
            return new SessionStatus(false, false);
        }
        synchronized (session) {
            session.lastSeen = Instant.now();
            return new SessionStatus(session.tokens.containsKey(OAuthProvider.SPOTIFY),
                    session.tokens.containsKey(OAuthProvider.GOOGLE));
        }
    }

    public Optional<String> accessToken(String sessionId, OAuthProvider provider) {
        removeExpiredSessions();
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        Session session = sessions.get(sessionId);
        if (session == null) {
            return Optional.empty();
        }
        synchronized (session) {
            OAuthTokenClient.TokenResponse token = session.tokens.get(provider);
            if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
                return Optional.empty();
            }
            session.lastSeen = Instant.now();
            return Optional.of(token.accessToken());
        }
    }

    public void remove(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            sessions.remove(sessionId);
        }
    }

    private Session requireSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new InvalidOAuthRequestException("OAuth session is missing");
        }
        Session session = sessions.get(sessionId);
        if (session == null) {
            throw new InvalidOAuthRequestException("OAuth session is missing or expired");
        }
        return session;
    }

    private void removeExpiredSessions() {
        Instant cutoff = Instant.now().minus(SESSION_TTL);
        sessions.entrySet().removeIf(entry -> entry.getValue().lastSeen.isBefore(cutoff));
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean secureEquals(String expected, String actual) {
        return java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public record Authorization(String sessionId, String state) {
    }

    public record SessionStatus(boolean spotifyConnected, boolean googleConnected) {
    }

    private static final class Session {
        private final Map<OAuthProvider, String> pendingStates = new EnumMap<>(OAuthProvider.class);
        private final Map<OAuthProvider, OAuthTokenClient.TokenResponse> tokens = new EnumMap<>(OAuthProvider.class);
        private Instant lastSeen = Instant.now();
    }
}
