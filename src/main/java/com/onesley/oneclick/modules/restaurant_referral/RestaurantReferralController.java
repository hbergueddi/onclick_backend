package com.onesley.oneclick.modules.restaurant_referral;

import com.onesley.oneclick.modules.restaurant_referral.internal.RestaurantReferralService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.onesley.oneclick.modules.restaurant_referral.api.RestaurantReferralDtos.ActivateRestaurantReferralDto;
import static com.onesley.oneclick.modules.restaurant_referral.api.RestaurantReferralDtos.RestaurantReferralDto;

/**
 * Parrainage RESTAURANT-à-RESTAURANT (owner → owner) — V100.
 *
 * <p>RBAC v2 senior strict : {@code @PreAuthorize hasAuthority('VERB:RESTAURANT_REFERRAL')}
 * UNIQUEMENT (jamais {@code hasRole}/{@code isAuthenticated}). Le scope fin (owner du resto concerné,
 * tenant {@code oneclick}) est appliqué en ABAC par {@link RestaurantReferralService}.
 */
@RestController
@RequestMapping("/api/restaurant-referrals")
@Tag(name = "RestaurantReferral", description = "Parrainage restaurant→restaurant (owner→owner) — points au parrain")
@RequiredArgsConstructor
public class RestaurantReferralController {

    private final RestaurantReferralService service;

    @GetMapping("/my-code")
    @Operation(summary = "Obtient/crée le code de parrainage du resto de l'owner (idempotent)")
    @PreAuthorize("hasAuthority('VIEW:RESTAURANT_REFERRAL') or hasAuthority('CREATE:RESTAURANT_REFERRAL')")
    public RestaurantReferralDto myCode(@RequestParam UUID restaurantId) {
        return service.getOrCreateMyCode(restaurantId);
    }

    @PostMapping("/activate")
    @Operation(summary = "Active un code de parrainage (owner du resto filleul) → points au parrain")
    @PreAuthorize("hasAuthority('CREATE:RESTAURANT_REFERRAL')")
    public ResponseEntity<RestaurantReferralDto> activate(@Valid @RequestBody ActivateRestaurantReferralDto body) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(service.activate(body.code(), body.refereeRestaurantId()));
    }

    @GetMapping("/mine")
    @Operation(summary = "Liste des parrainages activés portés par un resto (owner-scope)")
    @PreAuthorize("hasAuthority('VIEW:RESTAURANT_REFERRAL')")
    public List<RestaurantReferralDto> mine(@RequestParam UUID restaurantId) {
        return service.listMine(restaurantId);
    }
}
