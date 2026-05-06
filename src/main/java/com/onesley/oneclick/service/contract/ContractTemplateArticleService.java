package com.onesley.oneclick.service.contract;

import com.onesley.oneclick.dto.contract.ContractTemplateArticleDto;
import com.onesley.oneclick.mapper.contract.ContractTemplateArticleMapper;
import com.onesley.oneclick.repository.contract.ContractTemplateArticleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link ContractTemplateArticle} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class ContractTemplateArticleService {

    private final ContractTemplateArticleRepository repository;
    private final ContractTemplateArticleMapper mapper;

    public ContractTemplateArticleService(ContractTemplateArticleRepository repository, ContractTemplateArticleMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<ContractTemplateArticleDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<ContractTemplateArticleDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
