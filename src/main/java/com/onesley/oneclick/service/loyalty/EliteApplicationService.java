package com.onesley.oneclick.service.loyalty;

import com.onesley.oneclick.dto.loyalty.EliteApplicationDto;
import com.onesley.oneclick.mapper.loyalty.EliteApplicationMapper;
import com.onesley.oneclick.repository.loyalty.EliteApplicationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link EliteApplication} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class EliteApplicationService {

    private final EliteApplicationRepository repository;
    private final EliteApplicationMapper mapper;

    public EliteApplicationService(EliteApplicationRepository repository, EliteApplicationMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<EliteApplicationDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<EliteApplicationDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
