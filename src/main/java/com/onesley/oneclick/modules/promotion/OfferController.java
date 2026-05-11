package com.onesley.oneclick.modules.promotion;

import com.onesley.oneclick.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/offers")
@Tag(name = "Offers", description = "Offres / promotions par restaurant (§7)")
public class OfferController {

    private final OfferService service;

    public OfferController(OfferService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste paginée — filtres restaurantId + activeOnly")
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
    public OfferDto findById(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    @Operation(summary = "Crée une offre/promotion")
    public ResponseEntity<OfferDto> create(@Valid @RequestBody OfferCreateDto dto) {
        OfferDto o = service.create(dto);
        return ResponseEntity.created(URI.create("/api/offers/" + o.id())).body(o);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete d'une offre")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.softDelete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
