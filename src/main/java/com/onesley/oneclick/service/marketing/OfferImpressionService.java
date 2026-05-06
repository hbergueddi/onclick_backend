package com.onesley.oneclick.service.marketing;

import com.onesley.oneclick.dto.marketing.OfferImpressionDto;
import com.onesley.oneclick.mapper.marketing.OfferImpressionMapper;
import com.onesley.oneclick.repository.marketing.OfferImpressionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link OfferImpression} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class OfferImpressionService {

    private final OfferImpressionRepository repository;
    private final OfferImpressionMapper mapper;

    public OfferImpressionService(OfferImpressionRepository repository, OfferImpressionMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<OfferImpressionDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<OfferImpressionDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
