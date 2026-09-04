package com.playlistbridge.api.dto;

import com.playlistbridge.provider.model.SourcePlaylist;

import java.util.List;

public record PlaylistPageResponse(
        List<SourcePlaylist> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
