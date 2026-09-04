package com.playlistbridge.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class OAuthController {
    private final OAuthService oauthService;

    public OAuthController(OAuthService oauthService) {
        this.oauthService = oauthService;
    }

    @GetMapping({"/api/auth/{provider}", "/api/auth/{provider}/authorize", "/oauth2/authorization/{provider}"})
    public ResponseEntity<Void> authorize(
            @PathVariable String provider,
            @CookieValue(value = OAuthSessionStore.COOKIE_NAME, required = false) String sessionId,
            HttpServletRequest request) {
        try {
            OAuthService.AuthorizationStart start = oauthService.begin(OAuthProvider.fromRoute(provider), sessionId);
            ResponseCookie sessionCookie = sessionCookie(start.sessionId(), request.isSecure(), OAuthSessionStore.SESSION_MAX_AGE_SECONDS);
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(start.authorizationUri());
            headers.add(HttpHeaders.SET_COOKIE, sessionCookie.toString());
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        } catch (OAuthConfigurationException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        } catch (InvalidOAuthRequestException exception) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping({"/login/oauth2/code/{provider}", "/api/auth/{provider}/callback"})
    public ResponseEntity<?> callback(
            @PathVariable String provider,
            @CookieValue(value = OAuthSessionStore.COOKIE_NAME, required = false) String sessionId,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error) {
        try {
            URI redirect = oauthService.complete(OAuthProvider.fromRoute(provider), sessionId, state, code, error);
            return ResponseEntity.status(HttpStatus.FOUND).location(redirect).build();
        } catch (InvalidOAuthRequestException exception) {
            return ResponseEntity.badRequest().body(new OAuthError(exception.getMessage()));
        } catch (OAuthAuthorizationException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new OAuthError(exception.getMessage()));
        } catch (OAuthTokenExchangeException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new OAuthError("OAuth provider unavailable"));
        }
    }

    @GetMapping("/api/auth/session")
    public OAuthSessionStore.SessionStatus session(
            @CookieValue(value = OAuthSessionStore.COOKIE_NAME, required = false) String sessionId) {
        return oauthService.status(sessionId);
    }

    @DeleteMapping("/api/auth/session")
    public ResponseEntity<Void> logout(
            @CookieValue(value = OAuthSessionStore.COOKIE_NAME, required = false) String sessionId) {
        oauthService.remove(sessionId);
        ResponseCookie expired = sessionCookie("", false, 0);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, expired.toString()).build();
    }

    private ResponseCookie sessionCookie(String value, boolean secure, int maxAge) {
        return ResponseCookie.from(OAuthSessionStore.COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    public record OAuthError(String message) {
    }
}
