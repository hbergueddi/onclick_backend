package com.onesley.oneclick.service.admin;

import com.onesley.oneclick.dto.admin.AdminAuditLogDto;
import com.onesley.oneclick.mapper.admin.AdminAuditLogMapper;
import com.onesley.oneclick.repository.admin.AdminAuditLogRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link AdminAuditLog} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class AdminAuditLogService {

    private final AdminAuditLogRepository repository;
    private final AdminAuditLogMapper mapper;

    public AdminAuditLogService(AdminAuditLogRepository repository, AdminAuditLogMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<AdminAuditLogDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<AdminAuditLogDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
