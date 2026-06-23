package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.modules.restaurant.api.RestaurantCreateDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantProvisioningApi.ProvisionCommand;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantStaffCreateDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires isolés de {@link RestaurantProvisioningService} (BE-2, modules.restaurant).
 *
 * <p>Vérifie la création de la fiche restaurant (champs mappés depuis la demande) puis le
 * rattachement du gérant comme {@code owner} dans {@code restaurant_staffs}.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class RestaurantProvisioningServiceTest {

    @Mock RestaurantCatalogService catalogService;
    @Mock RestaurantSubResourceService subResourceService;
    @InjectMocks RestaurantProvisioningService service;

    @Test
    void provisionWithOwner_createsRestaurant_thenAttachesOwnerStaff() {
        UUID tenantId = UUID.randomUUID(), ownerId = UUID.randomUUID(), restoId = UUID.randomUUID();
        RestaurantDto created = mock(RestaurantDto.class);
        when(created.id()).thenReturn(restoId);
        when(catalogService.create(any())).thenReturn(created);

        UUID result = service.provisionWithOwner(
            new ProvisionCommand(tenantId, "Le Bistrot", "Casablanca", "12 rue X", "+212600", "marocaine", ownerId));

        assertThat(result).isEqualTo(restoId);
        ArgumentCaptor<RestaurantCreateDto> restoCap = ArgumentCaptor.forClass(RestaurantCreateDto.class);
        verify(catalogService).create(restoCap.capture());
        assertThat(restoCap.getValue().tenantId()).isEqualTo(tenantId);
        assertThat(restoCap.getValue().name()).isEqualTo("Le Bistrot");
        assertThat(restoCap.getValue().city()).isEqualTo("Casablanca");
        assertThat(restoCap.getValue().cuisine()).isEqualTo("marocaine");

        ArgumentCaptor<RestaurantStaffCreateDto> staffCap = ArgumentCaptor.forClass(RestaurantStaffCreateDto.class);
        verify(subResourceService).addStaff(eq(restoId), staffCap.capture());
        assertThat(staffCap.getValue().userId()).isEqualTo(ownerId);
        assertThat(staffCap.getValue().roleCode()).isEqualTo("owner");
    }
}
