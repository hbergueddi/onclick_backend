package com.onesley.oneclick.modules.support;

import com.onesley.oneclick.shared.PageResponse;
import com.onesley.oneclick.modules.support.internal.SupportService;
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

/**
 * Bug 32 (Batch D RBAC v2) — RBAC v2 senior strict hasAuthority('VERB:SUPPORT')
 */
@RestController
@RequestMapping("/api/support")
@Tag(name = "Support", description = "Tickets, messages, pièces jointes (§10)")
public class SupportController {

    private final SupportService service;

    public SupportController(SupportService service) {
        this.service = service;
    }

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
