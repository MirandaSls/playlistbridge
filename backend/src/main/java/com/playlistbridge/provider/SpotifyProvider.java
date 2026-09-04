package com.playlistbridge.provider;

import com.playlistbridge.provider.model.SourcePlaylist;
import com.playlistbridge.provider.model.SourceTrack;

import java.util.List;

public interface SpotifyProvider {
    boolean isConfigured();

    List<SourcePlaylist> listPlaylists();

    List<SourceTrack> getTracks(String playlistId);
}
