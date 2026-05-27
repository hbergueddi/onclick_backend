package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.loyalty.api.TierRestaurantOfferCreateDto;
import com.onesley.oneclick.modules.loyalty.api.TierRestaurantOfferPatchDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Tests unitaires Mockito de {@link TierRestaurantOfferService} (V48, repo mocké). */
@ExtendWith(MockitoExtension.class)
class TierRestaurantOfferServiceTest {

    @Mock TierRestaurantOfferRepository repo;
    @InjectMocks TierRestaurantOfferService service;

    @Test
    void list_mapsEntitiesToDtos() {
        TierRestaurantOffer o = new TierRestaurantOffer(UUID.randomUUID(), UUID.randomUUID(), "Ruby", "-10%");
        when(repo.findAllByDeletedAtIsNullOrderByTierNameAsc()).thenReturn(List.of(o));
        var dtos = service.list();
        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).tierName()).isEqualTo("Ruby");
        assertThat(dtos.get(0).offerLabel()).isEqualTo("-10%");
    }

    @Test
    void listByRestaurant_maps() {
        UUID rid = UUID.randomUUID();
        TierRestaurantOffer o = new TierRestaurantOffer(UUID.randomUUID(), rid, "Sapphire", "Dessert offert");
        when(repo.findByRestaurantIdAndDeletedAtIsNullOrderByTierNameAsc(rid)).thenReturn(List.of(o));
        var dtos = service.listByRestaurant(rid);
        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).restaurantId()).isEqualTo(rid);
    }

    @Test
    void create_appliesFieldsAndDefaults() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        var dto = service.create(new TierRestaurantOfferCreateDto(
            UUID.randomUUID(), "Émeraude", "Priorité réservation", "priorite", null, "desc", true));
        assertThat(dto.tierName()).isEqualTo("Émeraude");
        assertThat(dto.offerType()).isEqualTo("priorite");
        assertThat(dto.enabled()).isTrue();
    }

    @Test
    void create_minimal_keepsEntityDefaults() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        var dto = service.create(new TierRestaurantOfferCreateDto(
            UUID.randomUUID(), "Ruby", "Cadeau", null, null, null, null));
        assertThat(dto.offerType()).isEqualTo("remise");
        assertThat(dto.enabled()).isTrue();
    }

    @Test
    void patch_notFound_throws() {
        when(repo.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.patch(UUID.randomUUID(),
            new TierRestaurantOfferPatchDto(null, null, null, null, false)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void patch_appliesEnabled() {
        TierRestaurantOffer o = new TierRestaurantOffer(UUID.randomUUID(), UUID.randomUUID(), "Ruby", "-10%");
        when(repo.findByIdAndDeletedAtIsNull(o.getId())).thenReturn(Optional.of(o));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        var dto = service.patch(o.getId(), new TierRestaurantOfferPatchDto(null, null, null, null, false));
        assertThat(dto.enabled()).isFalse();
    }

    @Test
    void delete_notFound_throws() {
        when(repo.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void delete_marksDeleted() {
        TierRestaurantOffer o = new TierRestaurantOffer(UUID.randomUUID(), UUID.randomUUID(), "Ruby", "-10%");
        when(repo.findByIdAndDeletedAtIsNull(o.getId())).thenReturn(Optional.of(o));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        service.delete(o.getId());
        assertThat(o.getDeletedAt()).isNotNull();
    }
}
