package com.onesley.oneclick.service.admin;

import com.onesley.oneclick.dto.admin.ActionLogDto;
import com.onesley.oneclick.mapper.admin.ActionLogMapper;
import com.onesley.oneclick.repository.admin.ActionLogRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link ActionLog} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class ActionLogService {

    private final ActionLogRepository repository;
    private final ActionLogMapper mapper;

    public ActionLogService(ActionLogRepository repository, ActionLogMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<ActionLogDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<ActionLogDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
