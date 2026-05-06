package com.onesley.oneclick.controller.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantsConfigViewDto;
import com.onesley.oneclick.service.restaurant.RestaurantsConfigViewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller pour {@link RestaurantsConfigViewDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination,
 * mutations, sécurité @PreAuthorize).
 */
@RestController
@RequestMapping("/api/views/restaurants-configs")
@Tag(name = "RestaurantsConfigView", description = "Auto-generated controller for v_restaurants_config")
public class RestaurantsConfigViewController {

    private final RestaurantsConfigViewService service;

    public RestaurantsConfigViewController(RestaurantsConfigViewService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<RestaurantsConfigViewDto> findAll() {
        return service.findAll();
    }
}
