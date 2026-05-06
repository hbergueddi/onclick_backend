package com.onesley.oneclick.controller.support;

import com.onesley.oneclick.dto.support.ClientVisibleRatingDto;
import com.onesley.oneclick.service.support.ClientVisibleRatingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller pour {@link ClientVisibleRatingDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination,
 * mutations, sécurité @PreAuthorize).
 */
@RestController
@RequestMapping("/api/views/client-visible-ratings")
@Tag(name = "ClientVisibleRating", description = "Auto-generated controller for client_visible_ratings")
public class ClientVisibleRatingController {

    private final ClientVisibleRatingService service;

    public ClientVisibleRatingController(ClientVisibleRatingService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<ClientVisibleRatingDto> findAll() {
        return service.findAll();
    }
}
