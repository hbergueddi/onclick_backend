package com.onesley.oneclick.controller.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantsWithGroupViewDto;
import com.onesley.oneclick.service.restaurant.RestaurantsWithGroupViewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller pour {@link RestaurantsWithGroupViewDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination,
 * mutations, sécurité @PreAuthorize).
 */
@RestController
@RequestMapping("/api/views/restaurants-with-groups")
@Tag(name = "RestaurantsWithGroupView", description = "Auto-generated controller for v_restaurants_with_group")
public class RestaurantsWithGroupViewController {

    private final RestaurantsWithGroupViewService service;

    public RestaurantsWithGroupViewController(RestaurantsWithGroupViewService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<RestaurantsWithGroupViewDto> findAll() {
        return service.findAll();
    }
}
