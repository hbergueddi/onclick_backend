package com.onesley.oneclick.service.tenant;

import com.onesley.oneclick.dto.tenant.TenantEventDto;
import com.onesley.oneclick.mapper.tenant.TenantEventMapper;
import com.onesley.oneclick.repository.tenant.TenantEventRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link TenantEvent} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class TenantEventService {

    private final TenantEventRepository repository;
    private final TenantEventMapper mapper;

    public TenantEventService(TenantEventRepository repository, TenantEventMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<TenantEventDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<TenantEventDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
