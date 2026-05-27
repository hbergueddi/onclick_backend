package com.onesley.oneclick.modules.loyalty;

import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.modules.loyalty.api.TierRestaurantOfferCreateDto;
import com.onesley.oneclick.modules.loyalty.api.TierRestaurantOfferDto;
import com.onesley.oneclick.modules.loyalty.api.TierRestaurantOfferPatchDto;
import com.onesley.oneclick.modules.loyalty.internal.TierRestaurantOfferService;
import com.onesley.oneclick.security.RestaurantAccessGuard;
import com.onesley.oneclick.security.SecurityHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * REST controller {@code /api/tier-offers} — offres fidélité par niveau & restaurant
 * (V48). Matrice gérée FORGE (admin) ; lecture par-restaurant côté ProDesk.
 *
 * <p>RBAC v2 senior strict : {@code hasAuthority('VERB:TIER_OFFER')} uniquement.
 * CREATE/UPDATE/DELETE = SUPERADMIN (autorité). VIEW = SUPERADMIN/RESTAURATEUR/
 * GROUP_ADMIN ; la liste globale est admin-only (garde {@code isAdmin}) et la
 * lecture par-restaurant est scopée ABAC ({@code requireAdminOrActiveStaffOf}).
 */
@RestController
@RequestMapping("/api/tier-offers")
@Tag(name = "Tier Restaurant Offers", description = "Offres fidélité par niveau & restaurant")
@RequiredArgsConstructor
public class TierRestaurantOfferController {

    private final TierRestaurantOfferService service;
    private final RestaurantAccessGuard restaurantAccessGuard;

    @GetMapping
    @Operation(summary = "Toutes les offres par niveau (matrice admin FORGE)")
    @PreAuthorize("hasAuthority('VIEW:TIER_OFFER')")
    public List<TierRestaurantOfferDto> list() {
        // Liste globale (tous restaurants) = admin uniquement. Le restaurateur
        // passe par /by-restaurant/{id} (scopé ABAC).
        if (!SecurityHelper.isAdmin()) {
            throw new ForbiddenException("Liste globale réservée à l'administrateur");
        }
        return service.list();
    }

    @GetMapping("/by-restaurant/{restaurantId}")
    @Operation(summary = "Offres par niveau d'un restaurant (ProDesk)")
    @PreAuthorize("hasAuthority('VIEW:TIER_OFFER')")
    public List<TierRestaurantOfferDto> byRestaurant(@PathVariable UUID restaurantId) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(restaurantId);
        return service.listByRestaurant(restaurantId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crée une offre par niveau (FORGE)")
    @PreAuthorize("hasAuthority('CREATE:TIER_OFFER')")
    public TierRestaurantOfferDto create(@Valid @RequestBody TierRestaurantOfferCreateDto dto) {
        return service.create(dto);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Met à jour une offre par niveau (FORGE)")
    @PreAuthorize("hasAuthority('UPDATE:TIER_OFFER')")
    public TierRestaurantOfferDto patch(@PathVariable UUID id, @Valid @RequestBody TierRestaurantOfferPatchDto dto) {
        return service.patch(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprime (soft delete) une offre par niveau (FORGE)")
    @PreAuthorize("hasAuthority('DELETE:TIER_OFFER')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
