package com.onesley.oneclick.service.marketing;

import com.onesley.oneclick.dto.marketing.OfferDto;
import com.onesley.oneclick.mapper.marketing.OfferMapper;
import com.onesley.oneclick.repository.marketing.OfferRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link Offer} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class OfferService {

    private final OfferRepository repository;
    private final OfferMapper mapper;

    public OfferService(OfferRepository repository, OfferMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<OfferDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<OfferDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
