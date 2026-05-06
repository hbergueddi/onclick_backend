package com.onesley.oneclick.service.auth;

import com.onesley.oneclick.dto.auth.StaffRolePermissionDto;
import com.onesley.oneclick.mapper.auth.StaffRolePermissionMapper;
import com.onesley.oneclick.repository.auth.StaffRolePermissionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link StaffRolePermission} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class StaffRolePermissionService {

    private final StaffRolePermissionRepository repository;
    private final StaffRolePermissionMapper mapper;

    public StaffRolePermissionService(StaffRolePermissionRepository repository, StaffRolePermissionMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<StaffRolePermissionDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<StaffRolePermissionDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
