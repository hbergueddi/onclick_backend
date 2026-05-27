package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.loyalty.api.TierRestaurantOfferCreateDto;
import com.onesley.oneclick.modules.loyalty.api.TierRestaurantOfferDto;
import com.onesley.oneclick.modules.loyalty.api.TierRestaurantOfferPatchDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Service du domaine « offres fidélité par niveau » (tier_restaurant_offers).
 * Gating RBAC (TIER_OFFER) + ABAC porté par le contrôleur. Soft delete.
 */
@Service
@RequiredArgsConstructor
public class TierRestaurantOfferService {

    private final TierRestaurantOfferRepository repository;

    @Transactional(readOnly = true)
    public List<TierRestaurantOfferDto> list() {
        return repository.findAllByDeletedAtIsNullOrderByTierNameAsc()
            .stream().map(TierRestaurantOffer::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<TierRestaurantOfferDto> listByRestaurant(UUID restaurantId) {
        return repository.findByRestaurantIdAndDeletedAtIsNullOrderByTierNameAsc(restaurantId)
            .stream().map(TierRestaurantOffer::toDto).toList();
    }

    @Transactional
    public TierRestaurantOfferDto create(TierRestaurantOfferCreateDto dto) {
        TierRestaurantOffer o = new TierRestaurantOffer(
            UUID.randomUUID(), dto.restaurantId(), dto.tierName().trim(), dto.offerLabel().trim());
        if (dto.offerType() != null) o.setOfferType(dto.offerType());
        if (dto.offerValue() != null) o.setOfferValue(dto.offerValue());
        if (dto.description() != null) o.setDescription(dto.description());
        if (dto.enabled() != null) o.setEnabled(dto.enabled());
        return repository.save(o).toDto();
    }

    @Transactional
    public TierRestaurantOfferDto patch(UUID id, TierRestaurantOfferPatchDto dto) {
        TierRestaurantOffer o = repository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("TierRestaurantOffer", id));
        if (dto.offerLabel() != null && !dto.offerLabel().isBlank()) o.setOfferLabel(dto.offerLabel().trim());
        if (dto.offerType() != null) o.setOfferType(dto.offerType());
        if (dto.offerValue() != null) o.setOfferValue(dto.offerValue());
        if (dto.description() != null) o.setDescription(dto.description());
        if (dto.enabled() != null) o.setEnabled(dto.enabled());
        return repository.save(o).toDto();
    }

    @Transactional
    public void delete(UUID id) {
        TierRestaurantOffer o = repository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("TierRestaurantOffer", id));
        o.markDeleted();
        repository.save(o);
    }
}
