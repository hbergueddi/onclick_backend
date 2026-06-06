package com.onesley.oneclick.modules.analytics;

import com.onesley.oneclick.shared.PageResponse;
import com.onesley.oneclick.modules.analytics.api.AdminStatsDto;
import com.onesley.oneclick.modules.analytics.api.CrossTenantDtos.CrossTenantStatsDto;
import com.onesley.oneclick.modules.analytics.api.TenantClientDtos.TenantClientDetailDto;
import com.onesley.oneclick.modules.analytics.api.TenantClientDtos.TenantClientDto;
import com.onesley.oneclick.modules.analytics.api.TenantRestaurantDtos.TenantRestaurantDetailDto;
import com.onesley.oneclick.modules.analytics.api.TenantRestaurantDtos.TenantRestaurantDto;
import com.onesley.oneclick.modules.analytics.api.TenantReservationDtos.TenantReservationsResultDto;
import com.onesley.oneclick.modules.analytics.api.TenantOfferDtos.TenantOffersResultDto;
import com.onesley.oneclick.modules.analytics.api.TenantStaffDtos.TenantStaffResultDto;
import com.onesley.oneclick.modules.analytics.internal.AdminStatsService;
import com.onesley.oneclick.modules.analytics.internal.AnalyticsService;
import com.onesley.oneclick.modules.analytics.internal.CrossTenantStatsService;
import com.onesley.oneclick.modules.analytics.internal.TenantClientsService;
import com.onesley.oneclick.modules.analytics.internal.TenantRestaurantsService;
import com.onesley.oneclick.modules.analytics.internal.TenantReservationsService;
import com.onesley.oneclick.modules.analytics.internal.TenantOffersService;
import com.onesley.oneclick.modules.analytics.internal.TenantStaffService;
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
    private final CrossTenantStatsService crossTenantStatsService;
    private final TenantClientsService tenantClientsService;
    private final TenantRestaurantsService tenantRestaurantsService;
    private final TenantReservationsService tenantReservationsService;
    private final TenantOffersService tenantOffersService;
    private final TenantStaffService tenantStaffService;

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

    // ─── CrossTenantDashboard (C3, SUPERADMIN) — temps réel via STOMP /topic/admin/cross-tenant ──

    @GetMapping("/cross-tenant-stats")
    @Operation(
        summary = "KPIs cross-tenant (SUPERADMIN) — snapshot sur fenêtre N jours",
        description = "Agrégat par tenant (restos / CA Snap2Earn / tickets / clients / réservations + "
                    + "delta vs période précédente). Le dashboard est poussé en temps réel via STOMP "
                    + "(/topic/admin/cross-tenant) ; cet endpoint sert le snapshot REST initial."
    )
    @PreAuthorize("hasAuthority('VIEW:TENANTS')")
    public CrossTenantStatsDto crossTenantStats(@RequestParam(defaultValue = "30") int days) {
        return crossTenantStatsService.compute(days);
    }

    // ─── CRM tenant-admin « Mes clients » (C4.1, SUPERADMIN via VIEW:TENANTS) ──

    @GetMapping("/tenant-clients")
    @Operation(
        summary = "CRM d'un tenant — liste agrégée des clients (SUPERADMIN)",
        description = "Un client = a ≥ 1 ticket Snap2Earn OU ≥ 1 réservation dans un resto du tenant. "
                    + "Agrégats serveur-side (CA / visites / points / segment / resto favori). "
                    + "Native SQL groupé (anti N+1)."
    )
    @PreAuthorize("hasAuthority('VIEW:TENANTS')")
    public List<TenantClientDto> tenantClients(@RequestParam UUID tenantId) {
        return tenantClientsService.list(tenantId);
    }

    @GetMapping("/tenant-clients/{clientId}")
    @Operation(summary = "CRM d'un tenant — fiche 360° d'un client (KPIs + top restos + timeline)")
    @PreAuthorize("hasAuthority('VIEW:TENANTS')")
    public ResponseEntity<TenantClientDetailDto> tenantClientDetail(
        @PathVariable UUID clientId, @RequestParam UUID tenantId
    ) {
        TenantClientDetailDto dto = tenantClientsService.detail(tenantId, clientId);
        return dto == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(dto);
    }

    // ─── Portail tenant-admin « Mes restaurants » (C4.2, SUPERADMIN via VIEW:TENANTS) ──

    @GetMapping("/tenant-restaurants")
    @Operation(
        summary = "Restaurants d'un tenant — liste enrichie KPI 30j (SUPERADMIN)",
        description = "Liste des restaurants du tenant + CA/tickets/réservations/staff 30j par resto. "
                    + "Native SQL (restaurants.tenant_id direct)."
    )
    @PreAuthorize("hasAuthority('VIEW:TENANTS')")
    public List<TenantRestaurantDto> tenantRestaurants(@RequestParam UUID tenantId) {
        return tenantRestaurantsService.list(tenantId);
    }

    @GetMapping("/tenant-restaurants/{restaurantId}")
    @Operation(summary = "Restaurants d'un tenant — fiche (KPIs + delta + tendance + staff + offres + résas)")
    @PreAuthorize("hasAuthority('VIEW:TENANTS')")
    public ResponseEntity<TenantRestaurantDetailDto> tenantRestaurantDetail(
        @PathVariable UUID restaurantId, @RequestParam UUID tenantId
    ) {
        TenantRestaurantDetailDto dto = tenantRestaurantsService.detail(tenantId, restaurantId);
        return dto == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(dto);
    }

    // ─── Portail tenant-admin « Mes réservations » (C4.3, SUPERADMIN via VIEW:TENANTS) ──

    @GetMapping("/tenant-reservations")
    @Operation(
        summary = "Réservations d'un tenant — vue transverse + résumé (SUPERADMIN)",
        description = "Toutes les réservations des restaurants du tenant sur une fenêtre glissante "
                    + "(défaut 60j, passées + futures), enrichies resto + client, avec résumé "
                    + "(par statut, par resto, à venir, aujourd'hui, en attente). Native SQL."
    )
    @PreAuthorize("hasAuthority('VIEW:TENANTS')")
    public TenantReservationsResultDto tenantReservations(
        @RequestParam UUID tenantId, @RequestParam(defaultValue = "60") int days
    ) {
        return tenantReservationsService.list(tenantId, days);
    }

    // ─── Portail tenant-admin « Mes promos » (C4.4, SUPERADMIN via VIEW:TENANTS) ──

    @GetMapping("/tenant-offers")
    @Operation(
        summary = "Offres d'un tenant — liste enrichie + résumé (SUPERADMIN)",
        description = "Offres des restaurants du tenant (offers → restaurants → tenant) enrichies "
                    + "nom resto + impressions, avec résumé (actif/programmé/expiré). Native SQL. "
                    + "Les mutations réutilisent /api/offers (OfferController)."
    )
    @PreAuthorize("hasAuthority('VIEW:TENANTS')")
    public TenantOffersResultDto tenantOffers(@RequestParam UUID tenantId) {
        return tenantOffersService.list(tenantId);
    }

    // ─── Portail tenant-admin « Équipe » (C4.5, SUPERADMIN via VIEW:TENANTS) ──

    @GetMapping("/tenant-staff")
    @Operation(
        summary = "Équipe d'un tenant — staff agrégé cross-restos + résumé (SUPERADMIN)",
        description = "Staff de tous les restaurants du tenant (restaurant_staffs → restaurants → "
                    + "tenant), enrichi user + nom resto, avec résumé (owners/managers/staff/cross-resto). "
                    + "Native SQL. Les mutations réutilisent les endpoints staff existants."
    )
    @PreAuthorize("hasAuthority('VIEW:TENANTS')")
    public TenantStaffResultDto tenantStaff(@RequestParam UUID tenantId) {
        return tenantStaffService.list(tenantId);
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
