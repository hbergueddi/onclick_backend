package com.onesley.oneclick.controller.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantDto;
import com.onesley.oneclick.service.restaurant.RestaurantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/restaurants")
@Tag(name = "Restaurants", description = "Catalogue des restaurants partenaires")
public class RestaurantController {

    private final RestaurantService service;

    public RestaurantController(RestaurantService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un restaurant (page Spotlight)")
    public ResponseEntity<RestaurantDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/by-city")
    @Operation(summary = "Restaurants paginés par ville (page Explore)")
    public Page<RestaurantDto> findByCity(
        @RequestParam String city,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return service.findByCity(city, page, size);
    }

    @GetMapping("/by-tenant")
    @Operation(summary = "Restaurants d'un tenant (whitelabel)")
    public List<RestaurantDto> findByTenant(@RequestParam UUID tenantId) {
        return service.findActiveByTenant(tenantId);
    }

    @GetMapping("/count")
    @Operation(summary = "Nombre de restaurants pour une ville")
    public long count(@RequestParam String city) {
        return service.countByCity(city);
    }
}
