package com.onesley.oneclick.modules.promotion;

import com.onesley.oneclick.search.SearchRequest;
import com.onesley.oneclick.search.Searchable;
import com.onesley.oneclick.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Set;
import java.util.UUID;
import com.onesley.oneclick.modules.promotion.api.OfferCreateDto;
import com.onesley.oneclick.modules.promotion.api.OfferDto;
import com.onesley.oneclick.modules.promotion.internal.Offer;
import com.onesley.oneclick.modules.promotion.internal.OfferRepository;
import com.onesley.oneclick.modules.promotion.internal.OfferService;

@RestController
@RequestMapping("/api/offers")
@Tag(name = "Offers", description = "Offres / promotions par restaurant (§7)")
public class OfferController {

    /** Whitelist Phase 4 §6.3 — champs filtrables/sortables. */
    private static final Set<String> SEARCHABLE_FIELDS = Set.of(
        "tenantId", "restaurantId", "title",
        "startsAt", "expiresAt", "createdAt", "updatedAt"
    );

    private final OfferService service;
    private final OfferRepository offerRepository;

    public OfferController(OfferService service, OfferRepository offerRepository) {
        this.service = service;
        this.offerRepository = offerRepository;
    }

    @GetMapping
    @Operation(summary = "Liste paginée — filtres restaurantId + activeOnly")
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("isAuthenticated()")
    public OfferDto findById(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    @Operation(summary = "Crée une offre/promotion")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'GROUP_ADMIN', 'RESTAURATEUR')")
    public ResponseEntity<OfferDto> create(@Valid @RequestBody OfferCreateDto dto) {
        OfferDto o = service.create(dto);
        return ResponseEntity.created(URI.create("/api/offers/" + o.id())).body(o);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete d'une offre")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'GROUP_ADMIN', 'RESTAURATEUR')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.softDelete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping("/search")
    @Operation(summary = "Recherche dynamique (Phase 4 §6.3) — 12 opérateurs + whitelist")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<OfferDto> search(@RequestBody SearchRequest req) {
        return PageResponse.from(
            Searchable.execute(offerRepository, req, SEARCHABLE_FIELDS, Offer::toDto)
        );
    }
}
