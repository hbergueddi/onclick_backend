package com.onesley.oneclick.service.support;

import com.onesley.oneclick.dto.support.RuleTemplateDto;
import com.onesley.oneclick.mapper.support.RuleTemplateMapper;
import com.onesley.oneclick.repository.support.RuleTemplateRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link RuleTemplate} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class RuleTemplateService {

    private final RuleTemplateRepository repository;
    private final RuleTemplateMapper mapper;

    public RuleTemplateService(RuleTemplateRepository repository, RuleTemplateMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<RuleTemplateDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<RuleTemplateDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
