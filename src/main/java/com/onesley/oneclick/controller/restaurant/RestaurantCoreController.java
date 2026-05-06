package com.onesley.oneclick.controller.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantCoreDto;
import com.onesley.oneclick.service.restaurant.RestaurantCoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/restaurants/core")
@Tag(name = "Restaurants (core view)", description = "Projection minimale pour les listes (Explore page)")
public class RestaurantCoreController {

    private final RestaurantCoreService service;

    public RestaurantCoreController(RestaurantCoreService service) {
        this.service = service;
    }

    @GetMapping("/by-city")
    @Operation(summary = "Restaurants par ville (12 colonnes, optimisé liste)")
    public Page<RestaurantCoreDto> findByCity(
        @RequestParam String city,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return service.findByCity(city, page, size);
    }

    @GetMapping("/active")
    @Operation(summary = "Tous les restaurants en statut actif")
    public List<RestaurantCoreDto> findActive() {
        return service.findActive();
    }

    @GetMapping("/count")
    @Operation(summary = "Nombre total de restaurants")
    public long count() {
        return service.countAll();
    }
}
