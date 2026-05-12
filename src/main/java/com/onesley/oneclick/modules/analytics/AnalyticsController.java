package com.onesley.oneclick.modules.analytics;

import com.onesley.oneclick.shared.PageResponse;
import com.onesley.oneclick.modules.analytics.internal.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static com.onesley.oneclick.modules.analytics.api.AnalyticsDtos.*;

@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics", description = "Search docs (tsvector), API clients, keys, webhooks (§13)")
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }
    

    // ─── API clients ─────────────────────────────────────────────────────────

    @GetMapping("/api-clients")
    public PageResponse<ApiClientDto> findAllClients(
        @RequestParam(required = false) UUID tenantId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(service.findAllClients(tenantId, page, size));
    }

    @GetMapping("/api-clients/{id}")
    public ApiClientDto findClientById(@PathVariable UUID id) { return service.findClientById(id); }

    @PostMapping("/api-clients")
    public ResponseEntity<ApiClientDto> createClient(@Valid @RequestBody ApiClientCreateDto dto) {
        ApiClientDto c = service.createClient(dto);
        return ResponseEntity.created(URI.create("/api/analytics/api-clients/" + c.id())).body(c);
    }

    // ─── API keys ────────────────────────────────────────────────────────────

    @GetMapping("/api-clients/{apiClientId}/keys")
    public List<ApiKeyDto> findKeysByClient(@PathVariable UUID apiClientId) {
        return service.findKeysByClient(apiClientId);
    }

    @PostMapping("/api-keys")
    @Operation(summary = "Crée une clé API. Le hash et le prefix sont fournis par l'appelant (généré côté admin).")
    public ResponseEntity<ApiKeyDto> createKey(@Valid @RequestBody ApiKeyCreateDto dto) {
        ApiKeyDto k = service.createKey(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(k);
    }

    @DeleteMapping("/api-keys/{id}")
    @Operation(summary = "Revoke une clé API (revoked_at = now()).")
    public ResponseEntity<Void> revokeKey(@PathVariable UUID id) {
        service.revokeKey(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ─── Webhooks ────────────────────────────────────────────────────────────

    @GetMapping("/api-clients/{apiClientId}/webhooks")
    public List<WebhookDto> findWebhooksByClient(@PathVariable UUID apiClientId) {
        return service.findWebhooksByClient(apiClientId);
    }

    @PostMapping("/webhooks")
    public ResponseEntity<WebhookDto> createWebhook(@Valid @RequestBody WebhookCreateDto dto) {
        WebhookDto w = service.createWebhook(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(w);
    }

    @DeleteMapping("/webhooks/{id}")
    public ResponseEntity<Void> deleteWebhook(@PathVariable UUID id) {
        service.deleteWebhook(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ─── Webhook deliveries ──────────────────────────────────────────────────

    @GetMapping("/webhooks/{webhookId}/deliveries")
    public PageResponse<WebhookDeliveryDto> findDeliveriesByWebhook(
        @PathVariable UUID webhookId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(service.findDeliveriesByWebhook(webhookId, page, size));
    }

    @PostMapping("/deliveries")
    @Operation(summary = "Enregistre une tentative de delivery (à appeler après HTTP call sortant)")
    public ResponseEntity<WebhookDeliveryDto> recordDelivery(@Valid @RequestBody WebhookDeliveryCreateDto dto) {
        WebhookDeliveryDto d = service.recordDelivery(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(d);
    }
}
