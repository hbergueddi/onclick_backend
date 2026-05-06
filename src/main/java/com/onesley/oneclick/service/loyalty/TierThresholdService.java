package com.onesley.oneclick.service.loyalty;

import com.onesley.oneclick.dto.loyalty.TierThresholdDto;
import com.onesley.oneclick.mapper.loyalty.TierThresholdMapper;
import com.onesley.oneclick.repository.loyalty.TierThresholdRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link TierThreshold} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class TierThresholdService {

    private final TierThresholdRepository repository;
    private final TierThresholdMapper mapper;

    public TierThresholdService(TierThresholdRepository repository, TierThresholdMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<TierThresholdDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<TierThresholdDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
