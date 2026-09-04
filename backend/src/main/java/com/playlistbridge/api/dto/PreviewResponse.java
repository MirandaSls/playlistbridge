package com.playlistbridge.api.dto;

import com.playlistbridge.provider.model.MatchCandidate;

import java.util.List;

public record PreviewResponse(String playlistId, List<TrackPreview> tracks) {
    public record TrackPreview(
            String trackId,
            String title,
            String artist,
            List<MatchCandidate> candidates) {
    }
}
