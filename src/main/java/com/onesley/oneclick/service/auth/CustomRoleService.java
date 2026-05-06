package com.onesley.oneclick.service.auth;

import com.onesley.oneclick.dto.auth.CustomRoleDto;
import com.onesley.oneclick.mapper.auth.CustomRoleMapper;
import com.onesley.oneclick.repository.auth.CustomRoleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link CustomRole} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class CustomRoleService {

    private final CustomRoleRepository repository;
    private final CustomRoleMapper mapper;

    public CustomRoleService(CustomRoleRepository repository, CustomRoleMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<CustomRoleDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<CustomRoleDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
