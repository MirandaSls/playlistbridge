package com.playlistbridge.auth;

public interface OAuthTokenClient {
    TokenResponse exchange(OAuthProvider provider, String code, String redirectUri);

    record TokenResponse(String accessToken, String refreshToken) {
    }
}
