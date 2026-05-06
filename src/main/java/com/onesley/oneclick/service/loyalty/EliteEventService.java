package com.onesley.oneclick.service.loyalty;

import com.onesley.oneclick.dto.loyalty.EliteEventDto;
import com.onesley.oneclick.mapper.loyalty.EliteEventMapper;
import com.onesley.oneclick.repository.loyalty.EliteEventRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link EliteEvent} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class EliteEventService {

    private final EliteEventRepository repository;
    private final EliteEventMapper mapper;

    public EliteEventService(EliteEventRepository repository, EliteEventMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<EliteEventDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<EliteEventDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
