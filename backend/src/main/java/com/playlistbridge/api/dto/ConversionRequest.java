package com.playlistbridge.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record ConversionRequest(
        @NotBlank String playlistId,
        @NotBlank String title,
        @NotNull
        @Pattern(regexp = "private|unlisted|public", message = "must be private, unlisted, or public")
        String privacyStatus,
        @NotEmpty List<@Valid Selection> selections) {
    public record Selection(@NotBlank String trackId, @NotBlank String videoId) {
    }
}
