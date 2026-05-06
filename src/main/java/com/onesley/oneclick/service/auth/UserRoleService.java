package com.onesley.oneclick.service.auth;

import com.onesley.oneclick.dto.auth.UserRoleCreateDto;
import com.onesley.oneclick.dto.auth.UserRoleDto;
import com.onesley.oneclick.entity.auth.AppRole;
import com.onesley.oneclick.entity.auth.UserRole;
import com.onesley.oneclick.mapper.auth.UserRoleMapper;
import com.onesley.oneclick.repository.auth.UserRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service {@link UserRole} — orchestration métier au-dessus du repository.
 *
 * <p>Pattern pilote : transactional read-only par défaut (annotation classe), méthodes
 * d'écriture explicitement annotées {@code @Transactional}. Aucune dépendance
 * d'auth ici — la sécurité (qui peut écrire/lire) est gérée au niveau controller
 * via {@code @PreAuthorize} (Phase 6 senior dev).
 */
@Service
@Transactional(readOnly = true)
public class UserRoleService {

    private final UserRoleRepository repository;
    private final UserRoleMapper mapper;

    public UserRoleService(UserRoleRepository repository, UserRoleMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public List<UserRoleDto> findByUser(UUID userId) {
        return mapper.toDtoList(repository.findAllByUserId(userId));
    }

    public boolean userHasRole(UUID userId, AppRole role) {
        return repository.existsByUserIdAndRole(userId, role);
    }

    @Transactional
    public UserRoleDto assign(UserRoleCreateDto dto) {
        UserRole entity = mapper.toEntity(UUID.randomUUID(), dto);
        return mapper.toDto(repository.save(entity));
    }

    @Transactional
    public void revoke(UUID id) {
        repository.deleteById(id);
    }
}
