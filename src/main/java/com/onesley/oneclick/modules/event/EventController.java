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
import com.onesley.oneclick.modules.event.api.EventDtos.EventPatchDto;
import com.onesley.oneclick.modules.event.api.EventDtos.ParticipationCreateDto;
import com.onesley.oneclick.modules.event.api.EventDtos.ParticipationDto;
import com.onesley.oneclick.modules.event.internal.EventService;

/**
 * Bug 32 (Batch D RBAC v2) — RBAC v2 senior strict hasAuthority('VERB:EVENTS')
 */
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
    @PreAuthorize("hasAuthority('VIEW:EVENTS')")
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
    @PreAuthorize("hasAuthority('VIEW:EVENTS')")
    public EventDto findById(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    @PreAuthorize("hasAuthority('CREATE:EVENTS')")
    public ResponseEntity<EventDto> create(@Valid @RequestBody EventCreateDto dto) {
        EventDto e = service.create(dto);
        return ResponseEntity.created(URI.create("/api/events/" + e.id())).body(e);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DELETE:EVENTS')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.softDelete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ─── Participations / RSVP ───────────────────────────────────────────────

    @GetMapping("/{eventId}/participations")
    @Operation(summary = "Liste des RSVP d'un événement")
    @PreAuthorize("hasAuthority('VIEW:EVENTS')")
    public List<ParticipationDto> findParticipations(@PathVariable UUID eventId) {
        return service.findParticipations(eventId);
    }

    @PostMapping("/participations")
    @Operation(summary = "RSVP sur un événement (going|maybe|declined|attended)")
    @PreAuthorize("hasAuthority('UPDATE:EVENTS')")
    public ResponseEntity<ParticipationDto> rsvp(@Valid @RequestBody ParticipationCreateDto dto) {
        ParticipationDto p = service.rsvp(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(p);
    }

    @DeleteMapping("/participations/by-event/{eventId}/user/{userId}")
    @Operation(summary = "Sprint D — Cancel RSVP (decrement places_taken si going)")
    @PreAuthorize("hasAuthority('UPDATE:EVENTS')")
    public ResponseEntity<Void> cancelRsvp(@PathVariable UUID eventId, @PathVariable UUID userId) {
        service.cancelRsvp(eventId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/participations/by-user/{userId}")
    @Operation(summary = "Sprint D — RSVPs d'un user (Pocket Mes événements)")
    @PreAuthorize("hasAuthority('VIEW:EVENTS')")
    public List<ParticipationDto> findParticipationsByUser(@PathVariable UUID userId) {
        return service.findParticipationsByUser(userId);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Sprint D — Patch partiel d'un event (admin Elite)")
    @PreAuthorize("hasAuthority('UPDATE:EVENTS')")
    public EventDto patch(@PathVariable UUID id, @Valid @RequestBody EventPatchDto dto) {
        return service.patch(id, dto);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Elite club endpoints (Sprint D)
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/elite/active")
    @Operation(
        summary = "Sprint D — Events Elite actifs futurs (Pocket EliteClub)",
        description = "Filtre is_active=true + event_at > NOW. Sort event_at ASC."
    )
    @PreAuthorize("hasAuthority('VIEW:EVENTS')")
    public List<EventDto> findEliteActive() {
        return service.findEliteActiveUpcoming();
    }

    @GetMapping("/elite/all")
    @Operation(
        summary = "Sprint D — Tous les events Elite (admin Forge dashboard)",
        description = "Sort event_at DESC. Inclut inactifs."
    )
    @PreAuthorize("hasAuthority('VIEW:EVENTS')")
    public List<EventDto> findAllElite() {
        return service.findAllElite();
    }
}
