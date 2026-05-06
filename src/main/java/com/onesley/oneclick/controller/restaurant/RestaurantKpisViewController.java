package com.onesley.oneclick.controller.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantKpisViewDto;
import com.onesley.oneclick.service.restaurant.RestaurantKpisViewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller pour {@link RestaurantKpisViewDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination,
 * mutations, sécurité @PreAuthorize).
 */
@RestController
@RequestMapping("/api/views/restaurant-kpis")
@Tag(name = "RestaurantKpisView", description = "Auto-generated controller for v_restaurant_kpis")
public class RestaurantKpisViewController {

    private final RestaurantKpisViewService service;

    public RestaurantKpisViewController(RestaurantKpisViewService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<RestaurantKpisViewDto> findAll() {
        return service.findAll();
    }
}
