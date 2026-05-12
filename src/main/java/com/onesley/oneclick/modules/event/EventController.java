package com.onesley.oneclick.modules.event;

import com.onesley.oneclick.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static com.onesley.oneclick.modules.event.api.EventDtos.*;
import com.onesley.oneclick.modules.event.api.EventDtos;
import com.onesley.oneclick.modules.event.api.EventDtos.EventCreateDto;
import com.onesley.oneclick.modules.event.api.EventDtos.EventDto;
import com.onesley.oneclick.modules.event.api.EventDtos.ParticipationCreateDto;
import com.onesley.oneclick.modules.event.api.EventDtos.ParticipationDto;
import com.onesley.oneclick.modules.event.internal.EventService;

@RestController
@RequestMapping("/api/events")
@Tag(name = "Events", description = "Événements + RSVP (§9)")
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste paginée d'événements — filtres tenantId / restaurantId / upcomingOnly")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<EventDto> findAll(
        @RequestParam(required = false) UUID tenantId,
        @RequestParam(required = false) UUID restaurantId,
        @RequestParam(required = false) Boolean upcomingOnly,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(service.findAll(tenantId, restaurantId, upcomingOnly, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public EventDto findById(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'GROUP_ADMIN', 'RESTAURATEUR')")
    public ResponseEntity<EventDto> create(@Valid @RequestBody EventCreateDto dto) {
        EventDto e = service.create(dto);
        return ResponseEntity.created(URI.create("/api/events/" + e.id())).body(e);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'GROUP_ADMIN', 'RESTAURATEUR')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.softDelete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ─── Participations / RSVP ───────────────────────────────────────────────

    @GetMapping("/{eventId}/participations")
    @Operation(summary = "Liste des RSVP d'un événement")
    @PreAuthorize("isAuthenticated()")
    public List<ParticipationDto> findParticipations(@PathVariable UUID eventId) {
        return service.findParticipations(eventId);
    }

    @PostMapping("/participations")
    @Operation(summary = "RSVP sur un événement (going|maybe|declined|attended)")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ParticipationDto> rsvp(@Valid @RequestBody ParticipationCreateDto dto) {
        ParticipationDto p = service.rsvp(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(p);
    }
}
