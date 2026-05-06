package com.onesley.oneclick.service.admin;

import com.onesley.oneclick.dto.admin.LifecycleEventDto;
import com.onesley.oneclick.mapper.admin.LifecycleEventMapper;
import com.onesley.oneclick.repository.admin.LifecycleEventRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link LifecycleEvent} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class LifecycleEventService {

    private final LifecycleEventRepository repository;
    private final LifecycleEventMapper mapper;

    public LifecycleEventService(LifecycleEventRepository repository, LifecycleEventMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<LifecycleEventDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<LifecycleEventDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
