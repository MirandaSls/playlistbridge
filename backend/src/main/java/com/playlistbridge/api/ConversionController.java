package com.playlistbridge.api;

import com.playlistbridge.api.dto.ConversionRequest;
import com.playlistbridge.api.dto.ConversionResponse;
import com.playlistbridge.api.dto.PreviewRequest;
import com.playlistbridge.api.dto.PreviewResponse;
import com.playlistbridge.service.ConversionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversions")
public class ConversionController {
    private final ConversionService conversionService;

    public ConversionController(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @PostMapping("/preview")
    public PreviewResponse preview(@Valid @RequestBody PreviewRequest request) {
        return conversionService.preview(request.playlistId());
    }

    @PostMapping
    public ResponseEntity<ConversionResponse> convert(@Valid @RequestBody ConversionRequest request) {
        return ResponseEntity.accepted().body(conversionService.convert(request));
    }
}
