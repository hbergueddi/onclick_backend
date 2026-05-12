package com.onesley.oneclick.core.notification;

import com.onesley.oneclick.shared.PageResponse;
import com.onesley.oneclick.core.notification.internal.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static com.onesley.oneclick.core.notification.api.NotificationDtos.*;
import com.onesley.oneclick.core.notification.api.NotificationDtos;
import com.onesley.oneclick.core.notification.api.NotificationDtos.CampaignCreateDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.CampaignDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.DeviceTokenCreateDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.DeviceTokenDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.NotificationCreateDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.NotificationDto;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Notifications, campagnes, device tokens (§7 + core/notification)")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    // ─── Notifications ───────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Notifications paginées — filtres recipientUserId / unreadOnly")
    public PageResponse<NotificationDto> findAll(
        @RequestParam(required = false) UUID recipientUserId,
        @RequestParam(required = false) Boolean unreadOnly,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(service.findAll(recipientUserId, unreadOnly, page, size));
    }

    @PostMapping
    public ResponseEntity<NotificationDto> create(@Valid @RequestBody NotificationCreateDto dto) {
        NotificationDto n = service.create(dto);
        return ResponseEntity.created(URI.create("/api/notifications/" + n.id())).body(n);
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Marque la notification comme lue (read_at = now() si pas déjà lue)")
    public NotificationDto markRead(@PathVariable UUID id) {
        return service.markRead(id);
    }

    // ─── Campaigns ───────────────────────────────────────────────────────────

    @GetMapping("/campaigns/by-tenant/{tenantId}")
    public List<CampaignDto> findCampaignsByTenant(@PathVariable UUID tenantId) {
        return service.findCampaignsByTenant(tenantId);
    }

    @PostMapping("/campaigns")
    @Operation(summary = "Crée une campagne marketing. scheduledAt non null → status=scheduled.")
    public ResponseEntity<CampaignDto> createCampaign(@Valid @RequestBody CampaignCreateDto dto) {
        CampaignDto c = service.createCampaign(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(c);
    }

    // ─── Device tokens ───────────────────────────────────────────────────────

    @GetMapping("/tokens/by-user/{userId}")
    public List<DeviceTokenDto> findTokensByUser(@PathVariable UUID userId) {
        return service.findTokensByUser(userId);
    }

    @PostMapping("/tokens")
    @Operation(summary = "Enregistre (ou retourne le token existant) — upsert idempotent par token.")
    public ResponseEntity<DeviceTokenDto> registerToken(@Valid @RequestBody DeviceTokenCreateDto dto) {
        DeviceTokenDto t = service.registerToken(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(t);
    }

    @DeleteMapping("/tokens/{id}")
    public ResponseEntity<Void> unregisterToken(@PathVariable UUID id) {
        service.unregisterToken(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
