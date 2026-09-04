package com.playlistbridge.service;

import com.playlistbridge.provider.ProviderNotConfiguredException;
import com.playlistbridge.provider.SpotifyProvider;
import com.playlistbridge.provider.model.SourcePlaylist;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SpotifyService {
    private final SpotifyProvider provider;

    public SpotifyService(SpotifyProvider provider) {
        this.provider = provider;
    }

    public PlaylistPage listPlaylists(int page, int size) {
        if (!provider.isConfigured()) {
            throw new ProviderNotConfiguredException("Spotify OAuth is not configured");
        }
        List<SourcePlaylist> playlists = provider.listPlaylists();
        int from = Math.min(page * size, playlists.size());
        int to = Math.min(from + size, playlists.size());
        int totalPages = playlists.isEmpty() ? 0 : (playlists.size() + size - 1) / size;
        return new PlaylistPage(playlists.subList(from, to), page, size, playlists.size(), totalPages);
    }

    public List<com.playlistbridge.provider.model.SourceTrack> getTracks(String playlistId) {
        if (!provider.isConfigured()) {
            throw new ProviderNotConfiguredException("Spotify OAuth is not configured");
        }
        return provider.getTracks(playlistId);
    }

    public record PlaylistPage(
            List<SourcePlaylist> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
