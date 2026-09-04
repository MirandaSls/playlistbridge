package com.playlistbridge.api.dto;

public record ConversionResponse(
        String jobId,
        String status,
        String playlistId,
        String title,
        String privacyStatus,
        String targetPlaylistId,
        String targetPlaylistUrl,
        int totalTracks,
        int selectedTracks,
        int matchedTracks,
        int skippedTracks) {
}
