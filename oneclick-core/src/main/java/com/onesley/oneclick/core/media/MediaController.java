package com.onesley.oneclick.core.media;

import com.onesley.oneclick.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

import static com.onesley.oneclick.core.media.MediaDtos.*;

@RestController
@RequestMapping("/api/media")
@Tag(name = "Media", description = "Médias polymorphiques (image/video/audio/pdf) + pièces jointes (§14)")
public class MediaController {

    private final MediaService service;

    public MediaController(MediaService service) {
        this.service = service;
    }

    // ─── Media ───────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Médias paginés — filtres entityType / entityId / mediaType")
    public PageResponse<MediaDto> findAllMedia(
        @RequestParam(required = false) String entityType,
        @RequestParam(required = false) UUID entityId,
        @RequestParam(required = false) String mediaType,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(service.findAllMedia(entityType, entityId, mediaType, page, size));
    }

    @PostMapping
    public ResponseEntity<MediaDto> createMedia(@Valid @RequestBody MediaCreateDto dto) {
        MediaDto m = service.createMedia(dto);
        return ResponseEntity.created(URI.create("/api/media/" + m.id())).body(m);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMedia(@PathVariable UUID id) {
        service.softDeleteMedia(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ─── Files ───────────────────────────────────────────────────────────────

    @GetMapping("/files")
    @Operation(summary = "Pièces jointes paginées — filtres entityType / entityId")
    public PageResponse<FileDto> findAllFiles(
        @RequestParam(required = false) String entityType,
        @RequestParam(required = false) UUID entityId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(service.findAllFiles(entityType, entityId, page, size));
    }

    @PostMapping("/files")
    public ResponseEntity<FileDto> createFile(@Valid @RequestBody FileCreateDto dto) {
        FileDto f = service.createFile(dto);
        return ResponseEntity.created(URI.create("/api/media/files/" + f.id())).body(f);
    }

    @DeleteMapping("/files/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable UUID id) {
        service.softDeleteFile(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
