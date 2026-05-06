package com.onesley.oneclick.service.admin;

import com.onesley.oneclick.dto.admin.MonitorLogDto;
import com.onesley.oneclick.mapper.admin.MonitorLogMapper;
import com.onesley.oneclick.repository.admin.MonitorLogRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link MonitorLog} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class MonitorLogService {

    private final MonitorLogRepository repository;
    private final MonitorLogMapper mapper;

    public MonitorLogService(MonitorLogRepository repository, MonitorLogMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<MonitorLogDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<MonitorLogDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
