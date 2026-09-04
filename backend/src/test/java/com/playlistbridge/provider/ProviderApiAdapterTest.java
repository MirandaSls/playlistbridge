package com.playlistbridge.provider;

import com.playlistbridge.provider.adapter.SpotifyApiAdapter;
import com.playlistbridge.provider.adapter.YouTubeApiAdapter;
import com.playlistbridge.provider.model.MatchCandidate;
import com.playlistbridge.provider.model.SourcePlaylist;
import com.playlistbridge.provider.model.SourceTrack;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class ProviderApiAdapterTest {

    @Test
    void spotifyReadsAllPlaylistAndTrackPagesWithSessionBearerToken() {
        RestTemplate client = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
        SpotifyApiAdapter adapter = new SpotifyApiAdapter(client, "https://spotify.test/v1", "spotify-session-token");

        server.expect(requestTo("https://spotify.test/v1/me/playlists?limit=50&offset=0"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer spotify-session-token"))
                .andRespond(withSuccess("""
                        {"items":[{"id":"p1","name":"Road Trip","tracks":{"total":2}}],"limit":50,"offset":0,"total":2,"next":"https://spotify.test/v1/me/playlists?limit=50&offset=50"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://spotify.test/v1/me/playlists?limit=50&offset=50"))
                .andExpect(header("Authorization", "Bearer spotify-session-token"))
                .andRespond(withSuccess("""
                        {"items":[{"id":"p2","name":"Focus","tracks":{"total":1}}],"limit":50,"offset":50,"total":2,"next":null}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://spotify.test/v1/playlists/p1/tracks?limit=100&offset=0"))
                .andExpect(header("Authorization", "Bearer spotify-session-token"))
                .andRespond(withSuccess("""
                        {"items":[{"track":{"id":"t1","name":"Song One","artists":[{"name":"Artist One"}]}}],"next":"https://spotify.test/v1/playlists/p1/tracks?limit=100&offset=100"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://spotify.test/v1/playlists/p1/tracks?limit=100&offset=100"))
                .andExpect(header("Authorization", "Bearer spotify-session-token"))
                .andRespond(withSuccess("""
                        {"items":[{"track":{"id":"t2","name":"Song Two","artists":[{"name":"Artist Two"}]}}],"next":null}
                        """, MediaType.APPLICATION_JSON));

        List<SourcePlaylist> playlists = adapter.listPlaylists();
        List<SourceTrack> tracks = adapter.getTracks("p1");

        assertThat(playlists).containsExactly(
                new SourcePlaylist("p1", "Road Trip", 2), new SourcePlaylist("p2", "Focus", 1));
        assertThat(tracks).containsExactly(
                new SourceTrack("t1", "Song One", "Artist One"), new SourceTrack("t2", "Song Two", "Artist Two"));
        server.verify();
    }

    @Test
    void youtubeSearchAndPlaylistWritesUseBearerAndMetadataOnly() {
        RestTemplate client = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
        YouTubeApiAdapter adapter = new YouTubeApiAdapter(client, "https://youtube.test/youtube/v3", "youtube-session-token");

        server.expect(requestTo("https://youtube.test/youtube/v3/search?part=snippet&type=video&maxResults=5&q=Song%20One%20Artist%20One"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer youtube-session-token"))
                .andRespond(withSuccess("""
                        {"items":[{"id":{"videoId":"v1"},"snippet":{"title":"Song One","channelTitle":"Artist One"}}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://youtube.test/youtube/v3/playlists?part=snippet,status"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer youtube-session-token"))
                .andExpect(content().json("""
                        {"snippet":{"title":"Road Trip"},"status":{"privacyStatus":"private"}}
                        """))
                .andRespond(withSuccess("""
                        {"id":"yt-playlist-1"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://youtube.test/youtube/v3/playlistItems?part=snippet"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer youtube-session-token"))
                .andExpect(content().json("""
                        {"snippet":{"playlistId":"yt-playlist-1","resourceId":{"kind":"youtube#video","videoId":"v1"}}}
                        """))
                .andRespond(withSuccess("""
                        {"id":"item-1"}
                        """, MediaType.APPLICATION_JSON));

        List<MatchCandidate> matches = adapter.findMatches(new SourceTrack("t1", "Song One", "Artist One"));
        String playlistId = adapter.createPlaylist("Road Trip", "private");
        adapter.insertPlaylistItem(playlistId, "v1");

        assertThat(matches).singleElement().satisfies(match -> {
            assertThat(match.videoId()).isEqualTo("v1");
            assertThat(match.title()).isEqualTo("Song One");
            assertThat(match.channel()).isEqualTo("Artist One");
        });
        assertThat(playlistId).isEqualTo("yt-playlist-1");
        server.verify();
    }

    @Test
    void spotifyQuotaResponseStopsPaginationAndExposesQuotaFailure() {
        RestTemplate client = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
        SpotifyApiAdapter adapter = new SpotifyApiAdapter(client, "https://spotify.test/v1", "spotify-session-token");
        server.expect(requestTo("https://spotify.test/v1/me/playlists?limit=50&offset=0"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(adapter::listPlaylists)
                .isInstanceOf(ProviderQuotaExceededException.class)
                .hasMessageContaining("Spotify API request failed (429)");
        server.verify();
    }
}
