package com.playlistbridge.api;

import com.playlistbridge.provider.fake.FakeProviderConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeProviderConfiguration.class)
class PlaylistBridgeApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FakeProviderConfiguration.FakeSpotifyProvider spotify;

    @BeforeEach
    void resetProviders() {
        spotify.setConfigured(false);
    }

    @Test
    void healthReturnsServiceStatus() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.service").value("playlistbridge"));
    }

    @Test
    void browserRequestsAllowConfiguredFrontendOriginAndCredentials() throws Exception {
        mockMvc.perform(options("/api/auth/session")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void playlistsReturnClearServiceUnavailableWhenOAuthIsMissing() throws Exception {
        mockMvc.perform(get("/api/spotify/playlists"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message", containsString("Spotify OAuth")));
    }

    @Test
    void playlistsReturnPaginatedSourcePlaylistsWhenConfigured() throws Exception {
        spotify.setConfigured(true);

        mockMvc.perform(get("/api/spotify/playlists?page=0&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("playlist-1"))
                .andExpect(jsonPath("$.content[0].name").value("Morning Mix"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void previewReturnsPerTrackCandidates() throws Exception {
        spotify.setConfigured(true);

        mockMvc.perform(post("/api/conversions/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playlistId":"playlist-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playlistId").value("playlist-1"))
                .andExpect(jsonPath("$.tracks[0].trackId").value("track-1"))
                .andExpect(jsonPath("$.tracks[0].candidates[0].videoId").value("video-1"))
                .andExpect(jsonPath("$.tracks[0].candidates[0].confidence").value(0.96))
                .andExpect(jsonPath("$.tracks[0].candidates[0].status").value("MATCHED"));
    }

    @Test
    void previewRejectsBlankPlaylistIdWithJsonValidationError() throws Exception {
        mockMvc.perform(post("/api/conversions/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playlistId":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.playlistId").exists());
    }

    @Test
    void conversionReturnsJobAndResultSummary() throws Exception {
        spotify.setConfigured(true);

        mockMvc.perform(post("/api/conversions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playlistId":"playlist-1",
                                  "title":"My YouTube Mix",
                                  "privacyStatus":"private",
                                  "selections":[{"trackId":"track-1","videoId":"video-1"}]
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value("conversion-playlist-1"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.selectedTracks").value(1))
                .andExpect(jsonPath("$.matchedTracks").value(1))
                .andExpect(jsonPath("$.targetPlaylistId").value("youtube-playlist-1"))
                .andExpect(jsonPath("$.targetPlaylistUrl").value("https://www.youtube.com/playlist?list=youtube-playlist-1"))
                .andExpect(jsonPath("$.privacyStatus").value("private"));
    }

    @Test
    void conversionRejectsUnsupportedPrivacyStatusAndIncompleteSelection() throws Exception {
        mockMvc.perform(post("/api/conversions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playlistId":"playlist-1",
                                  "title":"My Mix",
                                  "privacyStatus":"friends",
                                  "selections":[{"trackId":"","videoId":""}]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.privacyStatus").exists())
                .andExpect(jsonPath("$.fieldErrors['selections[0].trackId']").exists())
                .andExpect(jsonPath("$.fieldErrors['selections[0].videoId']").exists());
    }
}
