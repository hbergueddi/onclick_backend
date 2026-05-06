package com.onesley.oneclick.service.admin;

import com.onesley.oneclick.dto.admin.SystemAlertDto;
import com.onesley.oneclick.mapper.admin.SystemAlertMapper;
import com.onesley.oneclick.repository.admin.SystemAlertRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link SystemAlert} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class SystemAlertService {

    private final SystemAlertRepository repository;
    private final SystemAlertMapper mapper;

    public SystemAlertService(SystemAlertRepository repository, SystemAlertMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<SystemAlertDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<SystemAlertDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
