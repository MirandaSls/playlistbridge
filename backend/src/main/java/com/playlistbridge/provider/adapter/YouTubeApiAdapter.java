package com.playlistbridge.provider.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.playlistbridge.provider.ProviderApiException;
import com.playlistbridge.provider.ProviderNotConfiguredException;
import com.playlistbridge.provider.ProviderQuotaExceededException;
import com.playlistbridge.provider.YouTubeProvider;
import com.playlistbridge.provider.model.MatchCandidate;
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
import java.util.List;
import java.util.function.Supplier;

public class YouTubeApiAdapter implements YouTubeProvider {
    public static final String DEFAULT_API_BASE_URL = "https://www.googleapis.com/youtube/v3";

    private final RestOperations client;
    private final String apiBaseUrl;
    private final Supplier<String> accessTokenSupplier;

    public YouTubeApiAdapter() {
        this(new RestTemplate(), DEFAULT_API_BASE_URL, "");
    }

    public YouTubeApiAdapter(RestOperations client, String apiBaseUrl, String accessToken) {
        this(client, apiBaseUrl, () -> accessToken);
    }

    public YouTubeApiAdapter(RestOperations client, String apiBaseUrl, Supplier<String> accessTokenSupplier) {
        this.client = client;
        this.apiBaseUrl = trimTrailingSlash(apiBaseUrl);
        this.accessTokenSupplier = accessTokenSupplier;
    }

    public boolean isConfigured() {
        return accessToken() != null && !accessToken().isBlank();
    }

    @Override
    public List<MatchCandidate> findMatches(SourceTrack track) {
        requireConfigured();
        URI requestUri = UriComponentsBuilder.fromUriString(apiBaseUrl)
                .path("/search")
                .queryParam("part", "snippet")
                .queryParam("type", "video")
                .queryParam("maxResults", 5)
                .queryParam("q", track.title() + " " + track.artist())
                .build().encode().toUri();
        JsonNode response = exchange(requestUri, HttpMethod.GET, null);
        List<MatchCandidate> matches = new ArrayList<>();
        JsonNode items = response.path("items");
        if (items.isArray()) {
            for (JsonNode item : items) {
                String videoId = text(item.path("id"), "videoId");
                if (videoId.isBlank()) {
                    continue;
                }
                String title = text(item.path("snippet"), "title");
                String channel = text(item.path("snippet"), "channelTitle");
                double confidence = confidence(track, title, channel);
                matches.add(new MatchCandidate(videoId, title, channel, confidence,
                        confidence >= 0.9 ? "MATCHED" : "CANDIDATE"));
            }
        }
        return List.copyOf(matches);
    }

    @Override
    public String createPlaylist(String title, String privacyStatus) {
        requireConfigured();
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.putObject("snippet").put("title", title);
        body.putObject("status").put("privacyStatus", privacyStatus);
        JsonNode response = exchange(uri("/playlists", "part", "snippet,status"), HttpMethod.POST, body);
        String id = text(response, "id");
        if (id.isBlank()) {
            throw new ProviderApiException("YouTube", 200, "playlist response did not contain id");
        }
        return id;
    }

    @Override
    public void insertPlaylistItem(String playlistId, String videoId) {
        requireConfigured();
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        ObjectNode snippet = body.putObject("snippet");
        snippet.put("playlistId", playlistId);
        snippet.putObject("resourceId").put("kind", "youtube#video").put("videoId", videoId);
        exchange(uri("/playlistItems", "part", "snippet"), HttpMethod.POST, body);
    }

    private JsonNode exchange(URI requestUri, HttpMethod method, JsonNode body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        try {
            ResponseEntity<JsonNode> response = client.exchange(
                    requestUri, method, new HttpEntity<>(body, headers), JsonNode.class);
            return response.getBody() == null ? JsonNodeFactory.instance.objectNode() : response.getBody();
        } catch (HttpStatusCodeException exception) {
            throw providerException(exception);
        }
    }

    private URI uri(String path, String name, String value) {
        return UriComponentsBuilder.fromUriString(apiBaseUrl)
                .path(path).queryParam(name, value).build().encode().toUri();
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new ProviderNotConfiguredException("YouTube OAuth is not configured");
        }
    }

    private String accessToken() {
        return accessTokenSupplier.get();
    }

    private static double confidence(SourceTrack track, String title, String channel) {
        boolean titleMatches = normalized(title).equals(normalized(track.title()));
        boolean artistMatches = normalized(channel).contains(normalized(track.artist()));
        if (titleMatches && artistMatches) {
            return 0.96;
        }
        if (titleMatches) {
            return 0.75;
        }
        return 0.4;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static String trimTrailingSlash(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static RuntimeException providerException(HttpStatusCodeException exception) {
        int status = exception.getStatusCode().value();
        String body = exception.getResponseBodyAsString();
        if (status == 429 || (status == 403 && body.toLowerCase().contains("quota"))) {
            return new ProviderQuotaExceededException("YouTube", status, body);
        }
        return new ProviderApiException("YouTube", status, body);
    }
}
