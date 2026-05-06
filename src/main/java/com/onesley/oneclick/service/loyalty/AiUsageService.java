package com.onesley.oneclick.service.loyalty;

import com.onesley.oneclick.dto.loyalty.AiUsageDto;
import com.onesley.oneclick.mapper.loyalty.AiUsageMapper;
import com.onesley.oneclick.repository.loyalty.AiUsageRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link AiUsage} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class AiUsageService {

    private final AiUsageRepository repository;
    private final AiUsageMapper mapper;

    public AiUsageService(AiUsageRepository repository, AiUsageMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<AiUsageDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<AiUsageDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
