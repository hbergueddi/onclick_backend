package com.onesley.oneclick.service.admin;

import com.onesley.oneclick.dto.admin.SystemAlertRuleDto;
import com.onesley.oneclick.mapper.admin.SystemAlertRuleMapper;
import com.onesley.oneclick.repository.admin.SystemAlertRuleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link SystemAlertRule} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class SystemAlertRuleService {

    private final SystemAlertRuleRepository repository;
    private final SystemAlertRuleMapper mapper;

    public SystemAlertRuleService(SystemAlertRuleRepository repository, SystemAlertRuleMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<SystemAlertRuleDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<SystemAlertRuleDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
