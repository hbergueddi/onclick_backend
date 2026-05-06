package com.onesley.oneclick.service.contract;

import com.onesley.oneclick.dto.contract.ContractTemplateDto;
import com.onesley.oneclick.mapper.contract.ContractTemplateMapper;
import com.onesley.oneclick.repository.contract.ContractTemplateRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link ContractTemplate} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class ContractTemplateService {

    private final ContractTemplateRepository repository;
    private final ContractTemplateMapper mapper;

    public ContractTemplateService(ContractTemplateRepository repository, ContractTemplateMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<ContractTemplateDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<ContractTemplateDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
