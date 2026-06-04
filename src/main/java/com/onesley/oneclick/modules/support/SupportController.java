package com.onesley.oneclick.modules.support;

import com.onesley.oneclick.shared.PageResponse;
import com.onesley.oneclick.modules.support.internal.SupportService;
import com.onesley.oneclick.security.SecurityHelper;
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

import static com.onesley.oneclick.modules.support.api.SupportDtos.*;
import lombok.RequiredArgsConstructor;

/**
 * Bug 32 (Batch D RBAC v2) — RBAC v2 senior strict hasAuthority('VERB:SUPPORT')
 */
@RestController
@RequestMapping("/api/support")
@Tag(name = "Support", description = "Tickets, messages, pièces jointes (§10)")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService service;

    // ─── Tickets ─────────────────────────────────────────────────────────────

    @GetMapping("/tickets")
    @Operation(summary = "Tickets paginés — filtres openedById / assignedToId / status")
    @PreAuthorize("hasAuthority('VIEW:SUPPORT')")
    public PageResponse<TicketDto> findAll(
        @RequestParam(required = false) UUID openedById,
        @RequestParam(required = false) UUID assignedToId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        // Anti-fuite : un non-admin ne liste QUE ses propres tickets (les détails
        // par-id sont déjà gardés par requireOwnerOrAdmin(openedById) côté service).
        if (!SecurityHelper.isAdmin()) {
            openedById = SecurityHelper.currentUserId();
        }
        return PageResponse.from(service.findAll(openedById, assignedToId, status, page, size));
    }

    @GetMapping("/tickets/{id}")
    @PreAuthorize("hasAuthority('VIEW:SUPPORT')")
    public TicketDto findById(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping("/tickets")
    @PreAuthorize("hasAuthority('CREATE:SUPPORT')")
    public ResponseEntity<TicketDto> create(@Valid @RequestBody TicketCreateDto dto) {
        TicketDto t = service.create(dto);
        return ResponseEntity.created(URI.create("/api/support/tickets/" + t.id())).body(t);
    }

    /** Corps minimal pour l'ouverture d'un ticket friends_cap (self-scope). */
    public record FriendsCapTicketDto(@jakarta.validation.constraints.NotNull UUID userId) {}

    @PostMapping("/friends-cap-ticket")
    @Operation(
        summary = "Ouvre un ticket « plafond d'amis atteint » — dédupliqué 1/24 h par user.",
        description = "Appelé par le Pocket quand l'ajout d'ami est refusé (plafond atteint). Réutilise la " +
                      "création de ticket (catégorie friends_cap). Dédup 1/24 h (409 si déjà ouvert). " +
                      "CREATE:SUPPORT (que le CLIENT détient) + ABAC self-scope (userId == soi)."
    )
    @PreAuthorize("hasAuthority('CREATE:SUPPORT')")
    public ResponseEntity<TicketDto> createFriendsCapTicket(@Valid @RequestBody FriendsCapTicketDto dto) {
        TicketDto t = service.createFriendsCapTicket(dto.userId());
        return ResponseEntity.created(URI.create("/api/support/tickets/" + t.id())).body(t);
    }

    @PatchMapping("/tickets/{id}")
    @Operation(summary = "Mise à jour status / priority / assignation. status=resolved → resolved_at, status=closed → closed_at.")
    @PreAuthorize("hasAuthority('UPDATE:SUPPORT')")
    public TicketDto update(@PathVariable UUID id, @Valid @RequestBody TicketUpdateDto dto) {
        return service.update(id, dto);
    }

    // ─── Messages (thread) ───────────────────────────────────────────────────

    @GetMapping("/tickets/{ticketId}/messages")
    @PreAuthorize("hasAuthority('VIEW:SUPPORT')")
    public List<MessageDto> findMessages(@PathVariable UUID ticketId) {
        return service.findMessages(ticketId);
    }

    @PostMapping("/messages")
    @PreAuthorize("hasAuthority('CREATE:SUPPORT')")
    public ResponseEntity<MessageDto> postMessage(@Valid @RequestBody MessageCreateDto dto) {
        MessageDto m = service.postMessage(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(m);
    }

    // ─── Attachments ─────────────────────────────────────────────────────────

    @GetMapping("/tickets/{ticketId}/attachments")
    @PreAuthorize("hasAuthority('VIEW:SUPPORT')")
    public List<AttachmentDto> findAttachments(@PathVariable UUID ticketId) {
        return service.findAttachments(ticketId);
    }

    @PostMapping("/attachments")
    @PreAuthorize("hasAuthority('CREATE:SUPPORT')")
    public ResponseEntity<AttachmentDto> attach(@Valid @RequestBody AttachmentCreateDto dto) {
        AttachmentDto a = service.attach(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(a);
    }
}
