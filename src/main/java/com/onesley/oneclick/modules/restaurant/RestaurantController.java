package com.onesley.oneclick.modules.restaurant;

import com.onesley.oneclick.search.SearchRequest;
import com.onesley.oneclick.search.Searchable;
import com.onesley.oneclick.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Set;
import java.util.UUID;
import com.onesley.oneclick.modules.restaurant.api.RestaurantCreateDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantDto;
import com.onesley.oneclick.modules.restaurant.internal.RestaurantCatalogService;
import com.onesley.oneclick.modules.restaurant.internal.RestaurantRepository;

@RestController
@RequestMapping("/api/restaurants")
@Tag(name = "Restaurants", description = "Catalogue restaurants partenaires")
public class RestaurantController {

    /** Whitelist Phase 4 §6.3 — champs filtrables/sortables. */
    private static final Set<String> SEARCHABLE_FIELDS = Set.of(
        "tenantId", "name", "city", "status",
        "latitude", "longitude", "createdAt", "updatedAt"
    );

    private final RestaurantCatalogService service;
    private final RestaurantRepository restaurantRepository;

    public RestaurantController(RestaurantCatalogService service, RestaurantRepository restaurantRepository) {
        this.service = service;
        this.restaurantRepository = restaurantRepository;
    }

    @GetMapping
    @Operation(summary = "Liste paginée des restaurants — filtres city + tenantId optionnels")
    public PageResponse<RestaurantDto> findAll(
        @RequestParam(required = false) String city,
        @RequestParam(required = false) UUID tenantId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(service.findAll(city, tenantId, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail restaurant par UUID")
    public RestaurantDto findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @Operation(summary = "Crée un restaurant")
    public ResponseEntity<RestaurantDto> create(@Valid @RequestBody RestaurantCreateDto dto) {
        RestaurantDto r = service.create(dto);
        return ResponseEntity.created(URI.create("/api/restaurants/" + r.id())).body(r);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete d'un restaurant")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.softDelete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping("/search")
    @Operation(summary = "Recherche dynamique (Phase 4 §6.3) — 12 opérateurs + whitelist")
    public PageResponse<RestaurantDto> search(@RequestBody SearchRequest req) {
        return PageResponse.from(
            Searchable.execute(restaurantRepository, req, SEARCHABLE_FIELDS, RestaurantDto::from)
        );
    }
}
