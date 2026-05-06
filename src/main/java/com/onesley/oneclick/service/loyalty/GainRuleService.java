package com.onesley.oneclick.service.loyalty;

import com.onesley.oneclick.dto.loyalty.GainRuleDto;
import com.onesley.oneclick.mapper.loyalty.GainRuleMapper;
import com.onesley.oneclick.repository.loyalty.GainRuleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link GainRule} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class GainRuleService {

    private final GainRuleRepository repository;
    private final GainRuleMapper mapper;

    public GainRuleService(GainRuleRepository repository, GainRuleMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<GainRuleDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<GainRuleDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
