package com.onesley.oneclick.core.identity.internal;

import com.onesley.oneclick.cache.CacheConfig;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.OneClickUserDetailsService;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.events.UserRegisteredEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import java.util.List;
import java.util.UUID;
import com.onesley.oneclick.core.identity.api.UserCreateDto;
import com.onesley.oneclick.core.identity.api.UserDto;
import com.onesley.oneclick.core.identity.api.UserUpdateDto;
import com.onesley.oneclick.core.identity.api.Role;
import com.onesley.oneclick.core.identity.api.Permission;
import com.onesley.oneclick.core.identity.api.Menu;
import com.onesley.oneclick.core.identity.api.MeContextDto;
import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserRepository;
import lombok.RequiredArgsConstructor;

/**
 * Service métier {@link User} — signup, lookup, update, soft delete.
 *
 * <p>Pattern : {@code @Transactional(readOnly=true)} par défaut, écritures explicites.
 * {@code passwordHash} jamais exposé en lecture (DTO le filtre).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    /**
     * Bug 34 — Service Spring Security responsable du cache {@code userDetails}.
     * Injecté ici pour appeler {@link OneClickUserDetailsService#evictUser(UUID)}
     * sur les mutations auth-critiques (password, soft-delete) — les autres
     * patches (nom, avatar, langue, téléphone) ne touchent pas aux champs
     * cachés dans {@code OneClickUserDetails}, donc pas d'eviction.
     */
    private final OneClickUserDetailsService userDetailsService;

    @PersistenceContext
    private EntityManager entityManager;

    public UserDto findById(UUID id) {
        User user = repository.findById(id)
            .filter(u -> u.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("User", id));
        return user.toDto();
    }

    @Cacheable(value = CacheConfig.CACHE_USERS_BY_EMAIL, key = "#email.toLowerCase()")
    public UserDto findByEmail(String email) {
        User user = repository.findByEmailIgnoreCase(email)
            .filter(u -> u.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("User by email: " + email));
        return user.toDto();
    }

    public Page<UserDto> findAll(int page, int size) {
        return repository.findAll(PageRequest.of(page, size)).map(User::toDto);
    }

    @Transactional
    public UserDto create(UserCreateDto dto) {
        if (repository.existsByEmailIgnoreCase(dto.email())) {
            throw new ConflictException("Email déjà utilisé : " + dto.email());
        }
        if (dto.phone() != null && repository.existsByPhone(dto.phone())) {
            throw new ConflictException("Téléphone déjà utilisé : " + dto.phone());
        }
        Role role = roleRepository.findById(dto.roleId())
            .orElseThrow(() -> new NotFoundException("Role", dto.roleId()));

        User user = new User(
            UUID.randomUUID(),
            role,
            dto.email().toLowerCase(),
            passwordEncoder.encode(dto.password()),
            dto.firstName(),
            dto.lastName()
        );
        if (dto.phone() != null) user.setPhone(dto.phone());
        if (dto.language() != null) user.setLanguage(dto.language());
        if (dto.tenantId() != null) {
            user.setTenant(entityManager.getReference(Tenant.class, dto.tenantId()));
        }
        User saved = repository.save(user);

        // Publish event Spring Modulith → Kafka topic 'user.registered'
        eventPublisher.publishEvent(new UserRegisteredEvent(
            saved.getId(), dto.tenantId(), dto.email().toLowerCase(),
            dto.firstName(), dto.lastName(),
            role.getCode(),
            dto.language() != null ? dto.language() : "fr",
            Instant.now()
        ));

        return saved.toDto();
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_USERS_BY_EMAIL, allEntries = true)
    public UserDto patch(UUID id, UserUpdateDto dto) {
        User user = repository.findById(id)
            .filter(u -> u.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("User", id));
        if (dto.firstName() != null) user.setFirstName(dto.firstName());
        if (dto.lastName() != null) user.setLastName(dto.lastName());
        if (dto.phone() != null) {
            // Check uniqueness only if phone changes
            if (!dto.phone().equals(user.getPhone()) && repository.existsByPhone(dto.phone())) {
                throw new ConflictException("Téléphone déjà utilisé : " + dto.phone());
            }
            user.setPhone(dto.phone());
        }
        if (dto.avatarUrl() != null) user.setAvatarUrl(dto.avatarUrl());
        if (dto.language() != null) user.setLanguage(dto.language());
        return repository.save(user).toDto();
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_USERS_BY_EMAIL, allEntries = true)
    public void softDelete(UUID id) {
        User user = repository.findById(id)
            .filter(u -> u.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("User", id));
        user.markDeleted();
        repository.save(user);
        // Bug 34 — purge UserDetails cache : user soft-deleted ne doit plus pouvoir
        // se réauthentifier via un JWT déjà émis (le converter renverra un token
        // sans autorités après ré-évaluation, et le filterChain bloquera).
        userDetailsService.evictUser(id);
    }

    // ───────────────────────────────────────────────────────────────────────
    //  Sécurité — Changement de password (owner exact, admins refusés)
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Change le mot de passe d'un user après validation du password courant.
     *
     * <p>Échoue avec {@link BadRequestException} si {@code currentPassword} ne
     * matche pas le hash en base. Le nouveau hash est calculé via le même
     * {@link PasswordEncoder} BCrypt strength 12 (cf {@code SecurityConfig}).
     *
     * <p>L'eviction du cache email n'est pas strictement nécessaire ici (le
     * password n'est pas dans le DTO), mais on garde la cohérence avec les
     * autres écritures.
     */
    @Transactional
    public void changePassword(UUID id, String currentPassword, String newPassword) {
        User user = repository.findById(id)
            .filter(u -> u.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("User", id));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("Mot de passe actuel invalide");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BadRequestException("Le nouveau mot de passe doit être différent");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        repository.save(user);
        // Bug 34 — purge UserDetails cache : le hash ayant changé, le payload
        // sérialisé Redis devient stale et empêcherait une réauthentification
        // immédiate avec le nouveau password si un autre flow (BasicAuth, etc.)
        // tape le {@code passwordHash} via {@code UserDetails.getPassword()}.
        userDetailsService.evictUser(id);
    }

    // ───────────────────────────────────────────────────────────────────────
    //  Lookups
    // ───────────────────────────────────────────────────────────────────────

    /** Lookup par téléphone — utilisé par les flows d'invitation d'amis (Pocket). */
    public UserDto findByPhone(String phone) {
        User user = repository.findByPhone(phone)
            .filter(u -> u.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("User by phone: " + phone));
        return user.toDto();
    }

    /**
     * Lookup par code de parrainage — résolution d'un code ami (OC-XXXXXX).
     *
     * <p>Utilisé par useCareChat/useAIAssistant côté Pocket pour résoudre un
     * code parrain saisi par l'utilisateur en UserDto cible (pour créer la
     * friendship). Endpoint exposé en {@code isAuthenticated()} — pas de leak
     * de données sensibles : seuls les champs publics du UserDto sont retournés.
     */
    public UserDto findByReferralCode(String referralCode) {
        User user = repository.findByReferralCode(referralCode)
            .filter(u -> u.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("User by referralCode: " + referralCode));
        return user.toDto();
    }

    /**
     * Batch lookup par liste d'UUIDs — anti N+1.
     *
     * <p>Remplace l'usage abusif de {@code POST /api/users/search} avec
     * {@code op:"IN"} (SUPERADMIN-only) pour les hooks qui enrichissent une
     * liste avec les profils correspondants (useFriendships, useTeamMembers,
     * useSupportTickets). Exposé en {@code isAuthenticated()} — l'appelant
     * doit déjà connaître les UUIDs (pas de leak global).
     *
     * <p>Conserve l'ordre d'entrée (utile pour les UIs qui mappent par index).
     */
    public java.util.List<UserDto> findAllByIds(java.util.Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return java.util.List.of();
        return repository.findAllByIds(ids).stream()
            .map(User::toDto)
            .toList();
    }

    /**
     * Liste paginée des users d'un rôle (code), avec filtrage tenant optionnel.
     *
     * <p>Utilisé par le picker frontend (Login client/staff) pour récupérer la
     * liste des comptes d'un tenant pour un rôle donné.
     */
    public Page<UserDto> findByRole(String roleCode, UUID tenantId, int page, int size) {
        return repository.findByRoleCode(roleCode, tenantId, PageRequest.of(page, size))
            .map(User::toDto);
    }

    /**
     * Distribution des utilisateurs par rôle — dashboard admin {@code GestionRoles}.
     *
     * <p>{@code LEFT JOIN} pour que les rôles à zéro utilisateur apparaissent quand
     * même ({@code count = 0}). Native SQL : agrégation {@code roles × users}
     * sans import cross-module (les deux tables vivent dans {@code core/identity}).
     */
    @SuppressWarnings("unchecked")
    public List<com.onesley.oneclick.core.identity.api.RoleDistributionDto> rolesDistribution() {
        List<Object[]> rows = entityManager.createNativeQuery("""
            SELECT r.code, COUNT(u.id)
              FROM roles r
              LEFT JOIN users u ON u.role_id = r.id AND u.deleted_at IS NULL
             GROUP BY r.code
             ORDER BY r.code
            """).getResultList();
        return rows.stream()
            .map(row -> new com.onesley.oneclick.core.identity.api.RoleDistributionDto(
                (String) row[0], ((Number) row[1]).longValue()))
            .toList();
    }

    /**
     * User courant (depuis le {@code sub} du JWT).
     *
     * <p>Retourne 401 si pas authentifié, 404 si le user n'existe plus en base
     * (token valide mais user soft-deleted entre temps — cas rare).
     */
    public UserDto findMe() {
        UUID currentId = SecurityHelper.currentUserId();
        if (currentId == null) {
            throw new BadRequestException("Authentification requise");
        }
        return findById(currentId);
    }

    /**
     * Contexte complet du user courant <strong>consolidé</strong> en une réponse :
     * profil + rôle + menus (sidebar) + permissions. Évite au front d'enchaîner
     * {@code /me} + {@code /me/permissions} et expose enfin les menus.
     *
     * <p>Construit ICI, dans le domaine identity, depuis {@code findByIdWithRoleAndPermissions}
     * (le graphe role→permissions→menu/action) — sans dépendre de l'adaptateur
     * Spring Security {@code OneClickUserDetails}. {@code @Transactional(readOnly)}
     * (hérité de la classe) ⇒ accès LAZY en session, on renvoie un DTO plat.
     */
    public MeContextDto findMeContext() {
        UUID currentId = SecurityHelper.currentUserId();
        if (currentId == null) {
            throw new BadRequestException("Authentification requise");
        }
        User u = repository.findByIdWithRoleAndPermissions(currentId)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("User", currentId));

        Role role = u.getRole();
        var permissions = new java.util.TreeSet<String>();
        var menusById = new java.util.LinkedHashMap<UUID, MeContextDto.MenuSummary>();
        if (role != null) {
            for (Permission p : role.getPermissions()) {
                if (p.getAction() != null && p.getMenu() != null) {
                    permissions.add(p.getAction().getCode() + ":" + p.getMenu().getCode());
                }
                Menu m = p.getMenu();
                if (m != null) {
                    menusById.putIfAbsent(m.getId(), new MeContextDto.MenuSummary(
                        m.getId(), m.getCode(), m.getName(), m.getIcon(), m.getPath(),
                        m.getParentId(), m.getSortOrder()));
                }
            }
        }
        var menus = menusById.values().stream()
            .sorted(java.util.Comparator.comparing(
                m -> m.sortOrder() == null ? Integer.MAX_VALUE : m.sortOrder()))
            .toList();

        return new MeContextDto(
            new MeContextDto.UserSummary(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(),
                u.getPhone(), u.getAvatarUrl(), u.getLanguage(), u.getStatus(), u.getTenantId()),
            role != null ? new MeContextDto.RoleSummary(role.getCode(), role.getName()) : null,
            menus,
            List.copyOf(permissions)
        );
    }
}
