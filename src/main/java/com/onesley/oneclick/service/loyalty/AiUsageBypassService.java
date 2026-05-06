package com.onesley.oneclick.service.loyalty;

import com.onesley.oneclick.dto.loyalty.AiUsageBypassDto;
import com.onesley.oneclick.mapper.loyalty.AiUsageBypassMapper;
import com.onesley.oneclick.repository.loyalty.AiUsageBypassRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link AiUsageBypass} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class AiUsageBypassService {

    private final AiUsageBypassRepository repository;
    private final AiUsageBypassMapper mapper;

    public AiUsageBypassService(AiUsageBypassRepository repository, AiUsageBypassMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<AiUsageBypassDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<AiUsageBypassDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
