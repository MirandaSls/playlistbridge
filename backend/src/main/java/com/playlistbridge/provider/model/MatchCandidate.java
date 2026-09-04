package com.playlistbridge.provider.model;

public record MatchCandidate(
        String videoId,
        String title,
        String channel,
        double confidence,
        String status) {
}
