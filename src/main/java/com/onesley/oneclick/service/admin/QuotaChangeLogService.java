package com.onesley.oneclick.service.admin;

import com.onesley.oneclick.dto.admin.QuotaChangeLogDto;
import com.onesley.oneclick.mapper.admin.QuotaChangeLogMapper;
import com.onesley.oneclick.repository.admin.QuotaChangeLogRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link QuotaChangeLog} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class QuotaChangeLogService {

    private final QuotaChangeLogRepository repository;
    private final QuotaChangeLogMapper mapper;

    public QuotaChangeLogService(QuotaChangeLogRepository repository, QuotaChangeLogMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<QuotaChangeLogDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<QuotaChangeLogDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
