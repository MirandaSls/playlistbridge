package com.playlistbridge.auth;

import com.playlistbridge.config.GoogleProperties;
import com.playlistbridge.config.SpotifyProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class RestClientOAuthTokenClient implements OAuthTokenClient {
    private final SpotifyProperties spotifyProperties;
    private final GoogleProperties googleProperties;
    private final RestClient restClient;

    public RestClientOAuthTokenClient(
            SpotifyProperties spotifyProperties,
            GoogleProperties googleProperties,
            RestClient.Builder restClientBuilder) {
        this.spotifyProperties = spotifyProperties;
        this.googleProperties = googleProperties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public TokenResponse exchange(OAuthProvider provider, String code, String redirectUri) {
        try {
            return switch (provider) {
                case SPOTIFY -> exchangeSpotify(code, redirectUri);
                case GOOGLE -> exchangeGoogle(code, redirectUri);
            };
        } catch (RestClientException exception) {
            throw new OAuthTokenExchangeException("OAuth provider token exchange failed");
        }
    }

    private TokenResponse exchangeSpotify(String code, String redirectUri) {
        MultiValueMap<String, String> form = form("grant_type", "authorization_code", "code", code,
                "redirect_uri", redirectUri);
        Map<?, ?> body = restClient.post()
                .uri("https://accounts.spotify.com/api/token")
                .headers(headers -> headers.setBasicAuth(spotifyProperties.getClientId(), spotifyProperties.getClientSecret()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        return tokenResponse(body);
    }

    private TokenResponse exchangeGoogle(String code, String redirectUri) {
        MultiValueMap<String, String> form = form("grant_type", "authorization_code", "code", code,
                "redirect_uri", redirectUri, "client_id", googleProperties.getClientId(),
                "client_secret", googleProperties.getClientSecret());
        Map<?, ?> body = restClient.post()
                .uri("https://oauth2.googleapis.com/token")
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        return tokenResponse(body);
    }

    private MultiValueMap<String, String> form(String... values) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        for (int i = 0; i < values.length; i += 2) {
            form.add(values[i], values[i + 1]);
        }
        return form;
    }

    private TokenResponse tokenResponse(Map<?, ?> body) {
        if (body == null || !(body.get("access_token") instanceof String accessToken) || accessToken.isBlank()) {
            throw new OAuthTokenExchangeException("OAuth provider returned no access token");
        }
        String refreshToken = body.get("refresh_token") instanceof String value ? value : "";
        return new TokenResponse(accessToken, refreshToken);
    }
}
