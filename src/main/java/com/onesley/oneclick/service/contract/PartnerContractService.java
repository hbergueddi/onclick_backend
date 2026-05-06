package com.onesley.oneclick.service.contract;

import com.onesley.oneclick.dto.contract.PartnerContractDto;
import com.onesley.oneclick.mapper.contract.PartnerContractMapper;
import com.onesley.oneclick.repository.contract.PartnerContractRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link PartnerContract} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class PartnerContractService {

    private final PartnerContractRepository repository;
    private final PartnerContractMapper mapper;

    public PartnerContractService(PartnerContractRepository repository, PartnerContractMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<PartnerContractDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<PartnerContractDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
