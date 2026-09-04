package com.playlistbridge.service;

import com.playlistbridge.api.dto.ConversionRequest;
import com.playlistbridge.provider.SpotifyProvider;
import com.playlistbridge.provider.YouTubeProvider;
import com.playlistbridge.provider.model.MatchCandidate;
import com.playlistbridge.provider.model.SourcePlaylist;
import com.playlistbridge.provider.model.SourceTrack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversionServiceTest {

    @Test
    void conversionCreatesPlaylistAndInsertsSelectedVideoIdsWithoutAudio() {
        RecordingYouTubeProvider youtube = new RecordingYouTubeProvider();
        ConversionService service = new ConversionService(
                new SpotifyService(new FixedSpotifyProvider()), youtube);

        var response = service.convert(new ConversionRequest(
                "source-playlist", "Road Trip", "private",
                List.of(new ConversionRequest.Selection("track-1", "video-1"))));

        assertThat(response.targetPlaylistId()).isEqualTo("created-playlist");
        assertThat(response.targetPlaylistUrl()).isEqualTo("https://www.youtube.com/playlist?list=created-playlist");
        assertThat(youtube.created).isEqualTo(new RecordingYouTubeProvider.Playlist("Road Trip", "private"));
        assertThat(youtube.inserted).containsExactly(new RecordingYouTubeProvider.Item("created-playlist", "video-1"));
    }

    @Test
    void conversionDoesNotInsertVideoOutsidePreviewCandidates() {
        RecordingYouTubeProvider youtube = new RecordingYouTubeProvider();
        ConversionService service = new ConversionService(
                new SpotifyService(new FixedSpotifyProvider()), youtube);

        var response = service.convert(new ConversionRequest(
                "source-playlist", "Road Trip", "private",
                List.of(new ConversionRequest.Selection("track-1", "arbitrary-video"))));

        assertThat(response.matchedTracks()).isZero();
        assertThat(youtube.inserted).isEmpty();
    }

    private static final class FixedSpotifyProvider implements SpotifyProvider {
        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public List<SourcePlaylist> listPlaylists() {
            return List.of();
        }

        @Override
        public List<SourceTrack> getTracks(String playlistId) {
            return List.of(new SourceTrack("track-1", "Song One", "Artist One"));
        }
    }

    private static final class RecordingYouTubeProvider implements YouTubeProvider {
        private Playlist created;
        private final List<Item> inserted = new ArrayList<>();

        @Override
        public List<MatchCandidate> findMatches(SourceTrack track) {
            return List.of(new MatchCandidate("video-1", "Song One", "Artist One", 0.96, "MATCHED"));
        }

        @Override
        public String createPlaylist(String title, String privacyStatus) {
            created = new Playlist(title, privacyStatus);
            return "created-playlist";
        }

        @Override
        public void insertPlaylistItem(String playlistId, String videoId) {
            inserted.add(new Item(playlistId, videoId));
        }

        private record Playlist(String title, String privacyStatus) {
        }

        private record Item(String playlistId, String videoId) {
        }
    }
}
