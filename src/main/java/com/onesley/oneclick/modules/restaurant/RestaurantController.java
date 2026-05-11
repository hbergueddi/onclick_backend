package com.onesley.oneclick.modules.restaurant;

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
@RequestMapping("/api/restaurants")
@Tag(name = "Restaurants", description = "Catalogue restaurants partenaires")
public class RestaurantController {

    private final RestaurantCatalogService service;

    public RestaurantController(RestaurantCatalogService service) {
        this.service = service;
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
}
