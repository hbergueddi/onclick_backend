package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.modules.restaurant.api.RestaurantCreateDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantProvisioningApi;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantStaffCreateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * BE-2 — implémentation de {@link RestaurantProvisioningApi}.
 *
 * <p>Réutilise les services internes du module : {@link RestaurantCatalogService#create} (fiche +
 * indexation/lifecycle_events) puis {@link RestaurantSubResourceService#addStaff} (rattachement du
 * gérant comme {@code owner}). Même transaction que l'appelant (store.decide) → atomicité
 * resto+staff ; un échec rollback le tout.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
class RestaurantProvisioningService implements RestaurantProvisioningApi {

    /** role_code applicatif du gérant dans restaurant_staffs (aligné sur le seed historique). */
    private static final String OWNER_STAFF_ROLE = "owner";

    private final RestaurantCatalogService catalogService;
    private final RestaurantSubResourceService subResourceService;

    @Override
    public UUID provisionWithOwner(ProvisionCommand cmd) {
        RestaurantDto restaurant = catalogService.create(new RestaurantCreateDto(
            cmd.tenantId(), cmd.name(), null, cmd.phone(), cmd.address(), cmd.city(),
            null, null, cmd.cuisine(), null, null));

        subResourceService.addStaff(restaurant.id(),
            new RestaurantStaffCreateDto(cmd.ownerUserId(), OWNER_STAFF_ROLE));

        log.info("[restaurant/provisioning] resto={} créé + owner={} rattaché (staff role={})",
            restaurant.id(), cmd.ownerUserId(), OWNER_STAFF_ROLE);
        return restaurant.id();
    }
}
