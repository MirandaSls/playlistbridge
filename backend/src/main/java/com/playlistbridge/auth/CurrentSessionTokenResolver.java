package com.playlistbridge.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class CurrentSessionTokenResolver {
    private final OAuthSessionStore sessionStore;

    public CurrentSessionTokenResolver(OAuthSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    public String spotifyAccessToken() {
        return resolve(OAuthProvider.SPOTIFY);
    }

    public String googleAccessToken() {
        return resolve(OAuthProvider.GOOGLE);
    }

    private String resolve(OAuthProvider provider) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            String sessionId = null;
            var cookie = request.getCookies();
            if (cookie != null) {
                for (var candidate : cookie) {
                    if (OAuthSessionStore.COOKIE_NAME.equals(candidate.getName())) {
                        sessionId = candidate.getValue();
                        break;
                    }
                }
            }
            return sessionStore.accessToken(sessionId, provider).orElse("");
        }
        return "";
    }
}
