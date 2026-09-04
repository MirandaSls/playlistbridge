package com.playlistbridge.provider.fake;

import com.playlistbridge.provider.SpotifyProvider;
import com.playlistbridge.provider.YouTubeProvider;
import com.playlistbridge.provider.model.MatchCandidate;
import com.playlistbridge.provider.model.SourcePlaylist;
import com.playlistbridge.provider.model.SourceTrack;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;

@TestConfiguration(proxyBeanMethods = false)
public class FakeProviderConfiguration {

    @Bean
    FakeSpotifyProvider fakeSpotifyProvider() {
        return new FakeSpotifyProvider();
    }

    @Bean
    FakeYouTubeProvider fakeYouTubeProvider() {
        return new FakeYouTubeProvider();
    }

    public static final class FakeSpotifyProvider implements SpotifyProvider {
        private boolean configured;

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public List<SourcePlaylist> listPlaylists() {
            return List.of(
                    new SourcePlaylist("playlist-1", "Morning Mix", 2),
                    new SourcePlaylist("playlist-2", "Road Trip", 1),
                    new SourcePlaylist("playlist-3", "Focus", 1));
        }

        @Override
        public List<SourceTrack> getTracks(String playlistId) {
            return List.of(
                    new SourceTrack("track-1", "First Song", "Example Artist"),
                    new SourceTrack("track-2", "Second Song", "Example Artist"));
        }

        public void setConfigured(boolean configured) {
            this.configured = configured;
        }
    }

    public static final class FakeYouTubeProvider implements YouTubeProvider {
        @Override
        public List<MatchCandidate> findMatches(SourceTrack track) {
            return List.of(new MatchCandidate("video-1", "First Song", "Example Artist", 0.96, "MATCHED"));
        }

        @Override
        public String createPlaylist(String title, String privacyStatus) {
            return "youtube-playlist-1";
        }
    }
}
