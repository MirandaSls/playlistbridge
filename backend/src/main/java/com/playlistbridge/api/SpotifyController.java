package com.playlistbridge.api;

import com.playlistbridge.api.dto.PlaylistPageResponse;
import com.playlistbridge.service.SpotifyService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/spotify")
@Validated
public class SpotifyController {
    private final SpotifyService spotifyService;

    public SpotifyController(SpotifyService spotifyService) {
        this.spotifyService = spotifyService;
    }

    @GetMapping("/playlists")
    public PlaylistPageResponse playlists(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        SpotifyService.PlaylistPage result = spotifyService.listPlaylists(page, size);
        return new PlaylistPageResponse(
                result.content(), result.page(), result.size(), result.totalElements(), result.totalPages());
    }
}
