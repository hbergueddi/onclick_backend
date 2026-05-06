package com.onesley.oneclick.service.admin;

import com.onesley.oneclick.dto.admin.AdminAuditLogViewDto;
import com.onesley.oneclick.mapper.admin.AdminAuditLogViewMapper;
import com.onesley.oneclick.repository.admin.AdminAuditLogViewRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link AdminAuditLogView} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class AdminAuditLogViewService {

    private final AdminAuditLogViewRepository repository;
    private final AdminAuditLogViewMapper mapper;

    public AdminAuditLogViewService(AdminAuditLogViewRepository repository, AdminAuditLogViewMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public List<AdminAuditLogViewDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
