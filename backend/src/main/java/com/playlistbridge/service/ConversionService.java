package com.playlistbridge.service;

import com.playlistbridge.api.dto.ConversionRequest;
import com.playlistbridge.api.dto.ConversionResponse;
import com.playlistbridge.api.dto.PreviewResponse;
import com.playlistbridge.provider.YouTubeProvider;
import com.playlistbridge.provider.model.SourceTrack;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ConversionService {
    private final SpotifyService spotifyService;
    private final YouTubeProvider youTubeProvider;

    public ConversionService(SpotifyService spotifyService, YouTubeProvider youTubeProvider) {
        this.spotifyService = spotifyService;
        this.youTubeProvider = youTubeProvider;
    }

    public PreviewResponse preview(String playlistId) {
        List<PreviewResponse.TrackPreview> tracks = spotifyService.getTracks(playlistId).stream()
                .map(track -> new PreviewResponse.TrackPreview(
                        track.id(), track.title(), track.artist(), youTubeProvider.findMatches(track)))
                .toList();
        return new PreviewResponse(playlistId, tracks);
    }

    public ConversionResponse convert(ConversionRequest request) {
        List<SourceTrack> sourceTracks = spotifyService.getTracks(request.playlistId());
        String destinationPlaylistId = youTubeProvider.createPlaylist(request.title(), request.privacyStatus());
        List<ConversionRequest.Selection> approvedSelections = new ArrayList<>();
        for (ConversionRequest.Selection selection : request.selections()) {
            sourceTracks.stream()
                    .filter(track -> track.id().equals(selection.trackId()))
                    .findFirst()
                    .filter(track -> youTubeProvider.findMatches(track).stream()
                            .anyMatch(candidate -> candidate.videoId().equals(selection.videoId())))
                    .ifPresent(ignored -> approvedSelections.add(selection));
        }
        approvedSelections.forEach(selection ->
                youTubeProvider.insertPlaylistItem(destinationPlaylistId, selection.videoId()));
        int matched = approvedSelections.size();
        int totalTracks = sourceTracks.size();
        int selectedTracks = request.selections().size();
        return new ConversionResponse(
                "conversion-" + request.playlistId(), "COMPLETED", request.playlistId(), request.title(),
                request.privacyStatus(), destinationPlaylistId,
                "https://www.youtube.com/playlist?list=" + destinationPlaylistId,
                totalTracks, selectedTracks, matched, Math.max(0, totalTracks - matched));
    }
}
