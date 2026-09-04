package com.playlistbridge.provider;

import com.playlistbridge.provider.model.MatchCandidate;
import com.playlistbridge.provider.model.SourceTrack;

import java.util.List;

public interface YouTubeProvider {
    List<MatchCandidate> findMatches(SourceTrack track);

    /** Creates destination playlist and returns provider playlist ID. */
    default String createPlaylist(String title, String privacyStatus) {
        return "";
    }

    /** Inserts video identity; implementations never transfer audio bytes. */
    default void insertPlaylistItem(String playlistId, String videoId) {
    }
}
