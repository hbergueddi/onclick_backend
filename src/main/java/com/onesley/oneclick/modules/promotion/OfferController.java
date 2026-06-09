package com.onesley.oneclick.modules.promotion;

import com.onesley.oneclick.search.SearchRequest;
import com.onesley.oneclick.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.onesley.oneclick.modules.promotion.api.OfferCreateDto;
import com.onesley.oneclick.modules.promotion.api.OfferDto;
import com.onesley.oneclick.modules.promotion.api.OfferImpressionDto;
import com.onesley.oneclick.modules.promotion.api.OfferPatchDto;
import com.onesley.oneclick.modules.promotion.api.OfferReadDto;
import com.onesley.oneclick.modules.promotion.internal.OfferReadService;
import com.onesley.oneclick.modules.promotion.internal.OfferService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/offers")
@Tag(name = "Offers", description = "Offres / promotions par restaurant (§7)")
@RequiredArgsConstructor
public class OfferController {

    /**
     * Whitelist Phase 4 §6.3 — champs filtrables/sortables.
     *
     * <p>Note : {@code tenantId} a été RETIRÉ (bug latent). La recherche s'exécute via une
     * {@code Specification} JPA sur l'entité {@link com.onesley.oneclick.modules.promotion.internal.Offer},
     * qui <b>ne mappe pas</b> de propriété {@code tenantId} (même si la colonne SQL {@code offers.tenant_id}
     * existe en base) — toute critère/tri sur ce champ résolvait un {@code Path} inexistant dans le
     * métamodèle → erreur. Le périmètre tenant est désormais imposé côté serveur par
     * {@code OfferService.search} (contrainte {@code restaurantId IN (restos visibles)}), via le JOIN
     * {@code offers → restaurants} (source de vérité du tenant : {@code restaurants.tenant_id}).
     */
    private static final Set<String> SEARCHABLE_FIELDS = Set.of(
        "restaurantId", "title", "type",
        "startsAt", "expiresAt", "createdAt", "updatedAt",
        // Boolean toggle "actif/inactif" — utilisé par Pocket (Spotlight,
        // Promos) pour filtrer les offres actives (enabled=true + expiresAt>now).
        "enabled"
    );

    private final OfferService service;
    private final OfferReadService readService;

    // Bug 32 (Batch A RBAC v2) — RBAC v2 senior strict hasAuthority('VERB:OFFERS')
    @GetMapping
    @Operation(summary = "Liste paginée — filtres restaurantId + activeOnly")
    @PreAuthorize("hasAuthority('VIEW:OFFERS')")
    public PageResponse<OfferDto> findAll(
        @RequestParam(required = false) UUID restaurantId,
        @RequestParam(required = false) Boolean activeOnly,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(service.findAll(restaurantId, activeOnly, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail offre par UUID")
    @PreAuthorize("hasAuthority('VIEW:OFFERS')")
    public OfferDto findById(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    @Operation(summary = "Crée une offre/promotion")
    @PreAuthorize("hasAuthority('CREATE:OFFERS')")
    public ResponseEntity<OfferDto> create(@Valid @RequestBody OfferCreateDto dto) {
        OfferDto o = service.create(dto);
        return ResponseEntity.created(URI.create("/api/offers/" + o.id())).body(o);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Mise à jour partielle d'une offre — null = pas de modification")
    @PreAuthorize("hasAuthority('UPDATE:OFFERS')")
    public OfferDto patch(@PathVariable UUID id, @Valid @RequestBody OfferPatchDto dto) {
        return service.patch(id, dto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete d'une offre")
    @PreAuthorize("hasAuthority('DELETE:OFFERS')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.softDelete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping("/search")
    @Operation(summary = "Recherche dynamique (Phase 4 §6.3) — 12 opérateurs + whitelist (scopée au périmètre tenant)")
    @PreAuthorize("hasAuthority('VIEW:OFFERS')")
    public PageResponse<OfferDto> search(@RequestBody SearchRequest req) {
        // Délégué au service : contraint la recherche au périmètre tenant visible (anti-énumération
        // cross-tenant), SUPERADMIN non scopé. La whitelist reste appliquée (champs + tri).
        return PageResponse.from(service.search(req, SEARCHABLE_FIELDS));
    }

    // ─── Impressions (tracking vues offres — offer_impressions V21) ──────────

    public record ImpressionRequest(String type) {}

    @GetMapping("/impressions")
    @Operation(summary = "Impressions (vues) des N derniers jours — dashboard exécutif Promotions (admin)")
    @PreAuthorize("hasAuthority('VIEW:ANALYTICS')")
    public List<OfferImpressionDto> impressions(
        @RequestParam(defaultValue = "60") @Min(1) @Max(365) int sinceDays
    ) {
        return service.listImpressions(sinceDays);
    }

    @PostMapping("/{id}/impressions")
    @Operation(summary = "Enregistre une vue d'offre par l'utilisateur courant")
    @PreAuthorize("hasAuthority('VIEW:OFFERS')")
    public ResponseEntity<Void> recordImpression(
        @PathVariable UUID id,
        @RequestBody(required = false) ImpressionRequest body
    ) {
        service.recordImpression(id, body != null ? body.type() : null);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // ─── Reads (suivi lu/non-lu par client — offer_reads V63) ─────────────────
    // RBAC : on réutilise VIEW:OFFERS (le client l'a déjà via V29 pour le catalogue
    // public). Pas de nouvelle autorité — calque du mark-read de core/notification
    // (réutilise UPDATE:NOTIFICATIONS au lieu d'ajouter un verbe). Le verrou est
    // l'ABAC dans OfferReadService : tout porte sur currentUser, jamais un userId
    // arbitraire → un user ne lit/écrit QUE ses propres états de lecture.

    @PostMapping("/{id}/read")
    @Operation(summary = "Marque l'offre comme lue pour l'utilisateur courant (upsert idempotent)")
    @PreAuthorize("hasAuthority('VIEW:OFFERS')")
    public ResponseEntity<OfferReadDto> markRead(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(readService.markRead(id));
    }

    @GetMapping("/reads")
    @Operation(summary = "Ids des offres lues par l'utilisateur courant (pour la logique « épinglées non-lues » front)")
    @PreAuthorize("hasAuthority('VIEW:OFFERS')")
    public List<UUID> readOfferIds() {
        return readService.listReadOfferIds();
    }
}
