package com.onesley.oneclick.service.loyalty;

import com.onesley.oneclick.dto.loyalty.PointDistributionDto;
import com.onesley.oneclick.mapper.loyalty.PointDistributionMapper;
import com.onesley.oneclick.repository.loyalty.PointDistributionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link PointDistribution} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class PointDistributionService {

    private final PointDistributionRepository repository;
    private final PointDistributionMapper mapper;

    public PointDistributionService(PointDistributionRepository repository, PointDistributionMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<PointDistributionDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<PointDistributionDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
