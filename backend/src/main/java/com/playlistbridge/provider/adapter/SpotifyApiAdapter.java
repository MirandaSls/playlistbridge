package com.playlistbridge.provider.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.playlistbridge.config.SpotifyProperties;
import com.playlistbridge.provider.ProviderApiException;
import com.playlistbridge.provider.ProviderNotConfiguredException;
import com.playlistbridge.provider.ProviderQuotaExceededException;
import com.playlistbridge.provider.SpotifyProvider;
import com.playlistbridge.provider.model.SourcePlaylist;
import com.playlistbridge.provider.model.SourceTrack;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class SpotifyApiAdapter implements SpotifyProvider {
    public static final String DEFAULT_API_BASE_URL = "https://api.spotify.com/v1";
    private static final int PLAYLIST_PAGE_SIZE = 50;
    private static final int TRACK_PAGE_SIZE = 100;

    private final RestOperations client;
    private final String apiBaseUrl;
    private final Supplier<String> accessTokenSupplier;

    public SpotifyApiAdapter(SpotifyProperties properties) {
        this(new RestTemplate(), DEFAULT_API_BASE_URL, properties::getAccessToken);
    }

    public SpotifyApiAdapter(RestOperations client, String apiBaseUrl, String accessToken) {
        this(client, apiBaseUrl, () -> accessToken);
    }

    public SpotifyApiAdapter(RestOperations client, String apiBaseUrl, SpotifyProperties properties) {
        this(client, apiBaseUrl, properties::getAccessToken);
    }

    public SpotifyApiAdapter(RestOperations client, String apiBaseUrl, Supplier<String> accessTokenSupplier) {
        this.client = client;
        this.apiBaseUrl = trimTrailingSlash(apiBaseUrl);
        this.accessTokenSupplier = accessTokenSupplier;
    }

    @Override
    public boolean isConfigured() {
        String token = bearerToken();
        return token != null && !token.isBlank();
    }

    @Override
    public List<SourcePlaylist> listPlaylists() {
        requireConfigured();
        List<SourcePlaylist> playlists = new ArrayList<>();
        URI next = uri("/me/playlists", "limit", PLAYLIST_PAGE_SIZE, "offset", 0);
        Set<URI> requested = new HashSet<>();
        while (next != null && requested.add(next)) {
            JsonNode page = get(next);
            JsonNode items = page.path("items");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    playlists.add(new SourcePlaylist(
                            text(item, "id"), text(item, "name"), item.path("tracks").path("total").asInt(0)));
                }
            }
            next = nextPage(page, "/me/playlists", PLAYLIST_PAGE_SIZE);
        }
        return List.copyOf(playlists);
    }

    @Override
    public List<SourceTrack> getTracks(String playlistId) {
        requireConfigured();
        List<SourceTrack> tracks = new ArrayList<>();
        String path = "/playlists/" + encodePath(playlistId) + "/tracks";
        URI next = uri(path, "limit", TRACK_PAGE_SIZE, "offset", 0);
        Set<URI> requested = new HashSet<>();
        while (next != null && requested.add(next)) {
            JsonNode page = get(next);
            JsonNode items = page.path("items");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    JsonNode track = item.path("track");
                    if (!track.isObject() || text(track, "id").isBlank()) {
                        continue;
                    }
                    JsonNode artists = track.path("artists");
                    String artist = artists.isArray() && !artists.isEmpty() ? text(artists.get(0), "name") : "";
                    tracks.add(new SourceTrack(text(track, "id"), text(track, "name"), artist));
                }
            }
            next = nextPage(page, path, TRACK_PAGE_SIZE);
        }
        return List.copyOf(tracks);
    }

    private JsonNode get(URI requestUri) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        try {
            ResponseEntity<JsonNode> response = client.exchange(
                    requestUri, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            return response.getBody() == null
                    ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                    : response.getBody();
        } catch (HttpStatusCodeException exception) {
            throw providerException("Spotify", exception);
        }
    }

    private URI nextPage(JsonNode page, String path, int pageSize) {
        JsonNode next = page.get("next");
        if (next != null && !next.isNull() && !next.asText().isBlank()) {
            return URI.create(next.asText());
        }
        int offset = page.path("offset").asInt(0);
        int total = page.path("total").asInt(0);
        JsonNode items = page.path("items");
        if (items.isArray() && !items.isEmpty() && offset + items.size() < total) {
            return uri(path, "limit", pageSize, "offset", offset + items.size());
        }
        return null;
    }

    private URI uri(String path, String firstName, int firstValue, String secondName, int secondValue) {
        return UriComponentsBuilder.fromUriString(apiBaseUrl)
                .path(path).queryParam(firstName, firstValue).queryParam(secondName, secondValue).build().toUri();
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new ProviderNotConfiguredException("Spotify OAuth is not configured");
        }
    }

    private String bearerToken() {
        return accessTokenSupplier.get();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static String trimTrailingSlash(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static String encodePath(String value) {
        return UriComponentsBuilder.fromPath(value).build().encode().toUriString();
    }

    private static RuntimeException providerException(String provider, HttpStatusCodeException exception) {
        int status = exception.getStatusCode().value();
        String body = exception.getResponseBodyAsString();
        if (status == 429 || (status == 403 && body.toLowerCase().contains("quota"))) {
            return new ProviderQuotaExceededException(provider, status, body);
        }
        return new ProviderApiException(provider, status, body);
    }
}
