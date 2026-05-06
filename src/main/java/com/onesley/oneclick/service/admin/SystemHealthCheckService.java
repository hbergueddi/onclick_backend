package com.onesley.oneclick.service.admin;

import com.onesley.oneclick.dto.admin.SystemHealthCheckDto;
import com.onesley.oneclick.mapper.admin.SystemHealthCheckMapper;
import com.onesley.oneclick.repository.admin.SystemHealthCheckRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link SystemHealthCheck} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class SystemHealthCheckService {

    private final SystemHealthCheckRepository repository;
    private final SystemHealthCheckMapper mapper;

    public SystemHealthCheckService(SystemHealthCheckRepository repository, SystemHealthCheckMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<SystemHealthCheckDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<SystemHealthCheckDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
