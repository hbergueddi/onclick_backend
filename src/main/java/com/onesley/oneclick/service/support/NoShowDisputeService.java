package com.onesley.oneclick.service.support;

import com.onesley.oneclick.dto.support.NoShowDisputeDto;
import com.onesley.oneclick.mapper.support.NoShowDisputeMapper;
import com.onesley.oneclick.repository.support.NoShowDisputeRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link NoShowDispute} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class NoShowDisputeService {

    private final NoShowDisputeRepository repository;
    private final NoShowDisputeMapper mapper;

    public NoShowDisputeService(NoShowDisputeRepository repository, NoShowDisputeMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<NoShowDisputeDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<NoShowDisputeDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
