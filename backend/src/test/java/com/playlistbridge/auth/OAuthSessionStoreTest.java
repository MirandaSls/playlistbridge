package com.playlistbridge.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthSessionStoreTest {
    @Test
    void exposesOnlyAccessTokenForCurrentProviderSession() {
        OAuthSessionStore store = new OAuthSessionStore();
        String sessionId = store.begin(null, OAuthProvider.SPOTIFY).sessionId();
        store.connect(sessionId, OAuthProvider.SPOTIFY,
                new OAuthTokenClient.TokenResponse("spotify-token", "refresh-token"));

        assertThat(store.accessToken(sessionId, OAuthProvider.SPOTIFY)).contains("spotify-token");
        assertThat(store.accessToken(sessionId, OAuthProvider.GOOGLE)).isEmpty();
    }

    @Test
    void rotatesKnownSessionWhenStartingAnotherProviderAuthorization() {
        OAuthSessionStore store = new OAuthSessionStore();
        String existingSessionId = store.begin(null, OAuthProvider.SPOTIFY).sessionId();
        store.connect(existingSessionId, OAuthProvider.SPOTIFY,
                new OAuthTokenClient.TokenResponse("spotify-token", ""));

        String rotatedSessionId = store.begin(existingSessionId, OAuthProvider.GOOGLE).sessionId();

        assertThat(rotatedSessionId).isNotEqualTo(existingSessionId);
        assertThat(store.accessToken(existingSessionId, OAuthProvider.SPOTIFY)).isEmpty();
        assertThat(store.accessToken(rotatedSessionId, OAuthProvider.SPOTIFY)).contains("spotify-token");
    }

    @Test
    void ignoresUnknownClientSuppliedSessionId() {
        OAuthSessionStore store = new OAuthSessionStore();

        String sessionId = store.begin("attacker-chosen-session", OAuthProvider.SPOTIFY).sessionId();

        assertThat(sessionId).isNotEqualTo("attacker-chosen-session");
    }
}
