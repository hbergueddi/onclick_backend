package com.onesley.oneclick.service.contract;

import com.onesley.oneclick.dto.contract.ContractHistoryDto;
import com.onesley.oneclick.mapper.contract.ContractHistoryMapper;
import com.onesley.oneclick.repository.contract.ContractHistoryRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link ContractHistory} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class ContractHistoryService {

    private final ContractHistoryRepository repository;
    private final ContractHistoryMapper mapper;

    public ContractHistoryService(ContractHistoryRepository repository, ContractHistoryMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<ContractHistoryDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<ContractHistoryDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
