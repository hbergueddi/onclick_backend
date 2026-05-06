package com.onesley.oneclick.service.loyalty;

import com.onesley.oneclick.dto.loyalty.TierRestaurantOfferDto;
import com.onesley.oneclick.mapper.loyalty.TierRestaurantOfferMapper;
import com.onesley.oneclick.repository.loyalty.TierRestaurantOfferRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link TierRestaurantOffer} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class TierRestaurantOfferService {

    private final TierRestaurantOfferRepository repository;
    private final TierRestaurantOfferMapper mapper;

    public TierRestaurantOfferService(TierRestaurantOfferRepository repository, TierRestaurantOfferMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<TierRestaurantOfferDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<TierRestaurantOfferDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
