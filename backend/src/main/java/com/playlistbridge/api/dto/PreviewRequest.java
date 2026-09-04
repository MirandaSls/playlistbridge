package com.playlistbridge.api.dto;

import jakarta.validation.constraints.NotBlank;

public record PreviewRequest(@NotBlank String playlistId) {
}
