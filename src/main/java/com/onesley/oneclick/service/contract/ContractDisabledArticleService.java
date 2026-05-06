package com.onesley.oneclick.service.contract;

import com.onesley.oneclick.dto.contract.ContractDisabledArticleDto;
import com.onesley.oneclick.mapper.contract.ContractDisabledArticleMapper;
import com.onesley.oneclick.repository.contract.ContractDisabledArticleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link ContractDisabledArticle} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class ContractDisabledArticleService {

    private final ContractDisabledArticleRepository repository;
    private final ContractDisabledArticleMapper mapper;

    public ContractDisabledArticleService(ContractDisabledArticleRepository repository, ContractDisabledArticleMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<ContractDisabledArticleDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<ContractDisabledArticleDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
