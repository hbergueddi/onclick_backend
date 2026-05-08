package com.onesley.oneclick.service.auth;

import com.onesley.oneclick.dto.auth.UserRoleCreateDto;
import com.onesley.oneclick.dto.auth.UserRoleDto;
import com.onesley.oneclick.entity.auth.Profile;
import com.onesley.oneclick.entity.auth.UserRole;
import com.onesley.oneclick.entity.shared.AppRole;
import com.onesley.oneclick.mapper.auth.UserRoleMapper;
import com.onesley.oneclick.repository.auth.UserRoleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service {@link UserRole} — orchestration métier au-dessus du repository.
 *
 * <p>Pattern : transactional read-only par défaut (annotation classe), méthodes
 * d'écriture explicitement annotées {@code @Transactional}. Aucune dépendance
 * d'auth ici — la sécurité (qui peut écrire/lire) est gérée au niveau controller
 * via {@code @PreAuthorize} (Phase 6 senior dev).
 *
 * <h3>Note jointures (passe 3)</h3>
 * <p>Pour {@link #assign(UserRoleCreateDto)} : on utilise
 * {@link EntityManager#getReference(Class, Object)} pour obtenir un proxy LAZY
 * du {@link Profile} cible — pas de SELECT déclenché tant qu'on ne lit pas
 * l'entité. Hibernate utilise uniquement {@code profile.id} pour l'INSERT
 * de la clé étrangère.
 */
@Service
@Transactional(readOnly = true)
public class UserRoleService {

    private final UserRoleRepository repository;
    private final UserRoleMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

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
        Profile userRef = entityManager.getReference(Profile.class, dto.userId());
        UserRole entity = mapper.toEntity(UUID.randomUUID(), userRef, dto);
        return mapper.toDto(repository.save(entity));
    }

    @Transactional
    public void revoke(UUID id) {
        repository.deleteById(id);
    }
}
