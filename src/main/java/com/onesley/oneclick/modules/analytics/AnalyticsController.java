package com.onesley.oneclick.modules.analytics;

import com.onesley.oneclick.shared.PageResponse;
import com.onesley.oneclick.modules.analytics.api.AdminStatsDto;
import com.onesley.oneclick.modules.analytics.internal.AdminStatsService;
import com.onesley.oneclick.modules.analytics.internal.AnalyticsService;
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

import static com.onesley.oneclick.modules.analytics.api.AnalyticsDtos.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics", description = "Search docs (tsvector), API clients, keys, webhooks (§13)")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService service;
    private final AdminStatsService adminStatsService;

    // ─── Admin stats — Sprint G.2.4 ────────────────────────────────────────

    @GetMapping("/admin-stats")
    @Operation(
        summary = "KPI platform-wide (users, restos, résas, loyalty, contrats, support) — admin dashboard",
        description = "Agrégats COUNT/SUM cross-modules en native SQL (anti-N+1). " +
                      "Filtre tenantId optionnel pour scoper au tenant."
    )
    // Bug 32 (Batch B RBAC v2) — RBAC v2 senior strict sur tous les endpoints du module analytics.
    @PreAuthorize("hasAuthority('VIEW:ANALYTICS')")
    public AdminStatsDto getAdminStats(@RequestParam(required = false) UUID tenantId) {
        return adminStatsService.computeStats(tenantId);
    }

    // ─── API clients ─────────────────────────────────────────────────────────

    @GetMapping("/api-clients")
    @PreAuthorize("hasAuthority('VIEW:ANALYTICS')")
    public PageResponse<ApiClientDto> findAllClients(
        @RequestParam(required = false) UUID tenantId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(service.findAllClients(tenantId, page, size));
    }

    @GetMapping("/api-clients/{id}")
    @PreAuthorize("hasAuthority('VIEW:ANALYTICS')")
    public ApiClientDto findClientById(@PathVariable UUID id) { return service.findClientById(id); }

    @PostMapping("/api-clients")
    @PreAuthorize("hasAuthority('CREATE:ANALYTICS')")
    public ResponseEntity<ApiClientDto> createClient(@Valid @RequestBody ApiClientCreateDto dto) {
        ApiClientDto c = service.createClient(dto);
        return ResponseEntity.created(URI.create("/api/analytics/api-clients/" + c.id())).body(c);
    }

    // ─── API keys ────────────────────────────────────────────────────────────

    @GetMapping("/api-clients/{apiClientId}/keys")
    @PreAuthorize("hasAuthority('VIEW:ANALYTICS')")
    public List<ApiKeyDto> findKeysByClient(@PathVariable UUID apiClientId) {
        return service.findKeysByClient(apiClientId);
    }

    @PostMapping("/api-keys")
    @Operation(summary = "Crée une clé API. Le hash et le prefix sont fournis par l'appelant (généré côté admin).")
    @PreAuthorize("hasAuthority('CREATE:ANALYTICS')")
    public ResponseEntity<ApiKeyDto> createKey(@Valid @RequestBody ApiKeyCreateDto dto) {
        ApiKeyDto k = service.createKey(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(k);
    }

    @DeleteMapping("/api-keys/{id}")
    @Operation(summary = "Revoke une clé API (revoked_at = now()).")
    @PreAuthorize("hasAuthority('DELETE:ANALYTICS')")
    public ResponseEntity<Void> revokeKey(@PathVariable UUID id) {
        service.revokeKey(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ─── Webhooks ────────────────────────────────────────────────────────────

    @GetMapping("/api-clients/{apiClientId}/webhooks")
    @PreAuthorize("hasAuthority('VIEW:ANALYTICS')")
    public List<WebhookDto> findWebhooksByClient(@PathVariable UUID apiClientId) {
        return service.findWebhooksByClient(apiClientId);
    }

    @PostMapping("/webhooks")
    @PreAuthorize("hasAuthority('CREATE:ANALYTICS')")
    public ResponseEntity<WebhookDto> createWebhook(@Valid @RequestBody WebhookCreateDto dto) {
        WebhookDto w = service.createWebhook(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(w);
    }

    @DeleteMapping("/webhooks/{id}")
    @PreAuthorize("hasAuthority('DELETE:ANALYTICS')")
    public ResponseEntity<Void> deleteWebhook(@PathVariable UUID id) {
        service.deleteWebhook(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ─── Webhook deliveries ──────────────────────────────────────────────────

    @GetMapping("/webhooks/{webhookId}/deliveries")
    @PreAuthorize("hasAuthority('VIEW:ANALYTICS')")
    public PageResponse<WebhookDeliveryDto> findDeliveriesByWebhook(
        @PathVariable UUID webhookId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(service.findDeliveriesByWebhook(webhookId, page, size));
    }

    @PostMapping("/deliveries")
    @Operation(summary = "Enregistre une tentative de delivery (à appeler après HTTP call sortant)")
    @PreAuthorize("hasAuthority('CREATE:ANALYTICS')")
    public ResponseEntity<WebhookDeliveryDto> recordDelivery(@Valid @RequestBody WebhookDeliveryCreateDto dto) {
        WebhookDeliveryDto d = service.recordDelivery(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(d);
    }
}
