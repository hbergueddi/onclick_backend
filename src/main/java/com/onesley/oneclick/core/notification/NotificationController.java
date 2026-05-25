package com.onesley.oneclick.core.notification;

import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.PageResponse;
import com.onesley.oneclick.core.notification.internal.FcmPushService;
import com.onesley.oneclick.core.notification.internal.NotificationService;
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

import static com.onesley.oneclick.core.notification.api.NotificationDtos.*;
import com.onesley.oneclick.core.notification.api.NotificationDtos;
import com.onesley.oneclick.core.notification.api.NotificationDtos.CampaignCreateDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.CampaignDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.DeviceTokenCreateDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.DeviceTokenDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.MarkAllReadResultDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.NotificationCreateDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.NotificationDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.PushPromoDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.PushReservationDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.PushResultDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.UnreadCountDto;
import lombok.RequiredArgsConstructor;

/**
 * Bug 32 (Batch D RBAC v2) — RBAC v2 senior strict hasAuthority('VERB:NOTIFICATIONS')
 */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Notifications, campagnes, device tokens (§7 + core/notification)")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;
    private final FcmPushService pushService;

    // ─── Notifications ───────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Notifications paginées — filtres recipientUserId / unreadOnly")
    @PreAuthorize("hasAuthority('VIEW:NOTIFICATIONS')")
    public PageResponse<NotificationDto> findAll(
        @RequestParam(required = false) UUID recipientUserId,
        @RequestParam(required = false) Boolean unreadOnly,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        // Anti-fuite : un non-admin (CLIENT/RESTAURATEUR/STAFF) ayant VIEW:NOTIFICATIONS
        // ne voit QUE ses propres notifications via cette liste ; les admins
        // (SUPERADMIN/GROUP_ADMIN) gardent le filtre recipientUserId libre.
        if (!SecurityHelper.isAdmin()) {
            recipientUserId = SecurityHelper.currentUserId();
        }
        return PageResponse.from(service.findAll(recipientUserId, unreadOnly, page, size));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CREATE:NOTIFICATIONS')")
    public ResponseEntity<NotificationDto> create(@Valid @RequestBody NotificationCreateDto dto) {
        NotificationDto n = service.create(dto);
        return ResponseEntity.created(URI.create("/api/notifications/" + n.id())).body(n);
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Marque la notification comme lue (read_at = now() si pas déjà lue)")
    @PreAuthorize("hasAuthority('UPDATE:NOTIFICATIONS')")  // self : tous rôles ont UPDATE:NOTIFICATIONS (V34) ; ownership via service
    public NotificationDto markRead(@PathVariable UUID id) {
        return service.markRead(id);
    }

    @GetMapping("/by-user/{userId}")
    @Operation(summary = "Cloche notifications — toutes (ou non lues si unreadOnly=true) triées DESC.")
    @PreAuthorize("hasAuthority('VIEW:NOTIFICATIONS')")  // self : tous rôles ont VIEW:NOTIFICATIONS (V34) ; ownership via service
    public List<NotificationDto> findByUser(
        @PathVariable UUID userId,
        @RequestParam(required = false) Boolean unreadOnly
    ) {
        return service.findByUser(userId, unreadOnly);
    }

    @GetMapping("/unread-count/by-user/{userId}")
    @Operation(summary = "Badge cloche — nombre de notifications non lues pour un user.")
    @PreAuthorize("hasAuthority('VIEW:NOTIFICATIONS')")  // self : tous rôles ont VIEW:NOTIFICATIONS (V34) ; ownership via service
    public UnreadCountDto unreadCountByUser(@PathVariable UUID userId) {
        return service.unreadCountByUser(userId);
    }

    @PatchMapping("/mark-all-read/by-user/{userId}")
    @Operation(summary = "Marque toutes les notifications non lues d'un user comme lues — renvoie le compteur.")
    @PreAuthorize("hasAuthority('UPDATE:NOTIFICATIONS')")  // self : tous rôles ont UPDATE:NOTIFICATIONS (V34) ; ownership via service
    public MarkAllReadResultDto markAllReadByUser(@PathVariable UUID userId) {
        return service.markAllReadByUser(userId);
    }

    // ─── Campaigns ───────────────────────────────────────────────────────────

    @GetMapping("/campaigns/by-tenant/{tenantId}")
    @PreAuthorize("hasAuthority('VIEW:NOTIFICATIONS')")
    public List<CampaignDto> findCampaignsByTenant(@PathVariable UUID tenantId) {
        return service.findCampaignsByTenant(tenantId);
    }

    @PostMapping("/campaigns")
    @Operation(summary = "Crée une campagne marketing. scheduledAt non null → status=scheduled.")
    @PreAuthorize("hasAuthority('CREATE:NOTIFICATIONS')")
    public ResponseEntity<CampaignDto> createCampaign(@Valid @RequestBody CampaignCreateDto dto) {
        CampaignDto c = service.createCampaign(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(c);
    }

    // ─── Device tokens ───────────────────────────────────────────────────────

    @GetMapping("/tokens/by-user/{userId}")
    @PreAuthorize("hasAuthority('VIEW:NOTIFICATIONS')")  // self : tous rôles ont VIEW:NOTIFICATIONS (V34) ; owner-check ci-dessous
    public List<DeviceTokenDto> findTokensByUser(@PathVariable UUID userId) {
        SecurityHelper.requireOwnerOrAdmin(userId);
        return service.findTokensByUser(userId);
    }

    @PostMapping("/tokens")
    @Operation(summary = "Enregistre (ou retourne le token existant) — upsert idempotent par token.")
    // UPDATE (pas CREATE) : upsert idempotent du token de l'user courant. Tous rôles
    // ont UPDATE:NOTIFICATIONS (V34) ; CREATE reste admin (create/createCampaign).
    @PreAuthorize("hasAuthority('UPDATE:NOTIFICATIONS')")
    public ResponseEntity<DeviceTokenDto> registerToken(@Valid @RequestBody DeviceTokenCreateDto dto) {
        SecurityHelper.requireOwnerOrAdmin(dto.userId());
        DeviceTokenDto t = service.registerToken(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(t);
    }

    @DeleteMapping("/tokens/{id}")
    @PreAuthorize("hasAuthority('DELETE:NOTIFICATIONS')")
    public ResponseEntity<Void> unregisterToken(@PathVariable UUID id) {
        service.unregisterToken(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ─── Push FCM (Phase B.8 — port send-*-push Edge Functions) ─────────────

    @PostMapping("/push/promo")
    @Operation(summary = "Push FCM promo — fan-out vers tous les device_tokens des userIds cibles. Stub si FCM non configuré.")
    @PreAuthorize("hasAuthority('CREATE:NOTIFICATIONS')")
    public PushResultDto pushPromo(@Valid @RequestBody PushPromoDto dto) {
        return pushService.sendPromo(dto);
    }

    @PostMapping("/push/reservation")
    @Operation(summary = "Push FCM réservation — fan-out vers les device_tokens du recipient (client OU staff). Stub si FCM non configuré.")
    @PreAuthorize("hasAuthority('CREATE:NOTIFICATIONS')")
    public PushResultDto pushReservation(@Valid @RequestBody PushReservationDto dto) {
        return pushService.sendReservation(dto);
    }
}
