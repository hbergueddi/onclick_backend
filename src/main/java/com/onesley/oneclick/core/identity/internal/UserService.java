package com.onesley.oneclick.core.identity.internal;

import com.onesley.oneclick.cache.CacheConfig;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.OneClickUserDetailsService;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.events.UserRegisteredEvent;
import com.onesley.oneclick.shared.events.AccountDeletedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.factory.annotation.Value;
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
import com.onesley.oneclick.core.identity.api.UserRegisterDto;
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

    /**
     * P1 enrollment — quand {@code true}, le signup public crée le compte en
     * {@code pending_email_verification} : le user doit valider l'OTP envoyé par email
     * (POST /api/auth/otp/request signup → /verify) avant de pouvoir se connecter.
     * Défaut {@code false} (rétro-compat : clients web/natifs déjà déployés sans écran OTP) ;
     * activable en prod via {@code APP_AUTH_EMAIL_VERIFICATION_REQUIRED=true} quand tous les
     * clients embarquent l'écran OTP.
     */
    @Value("${app.auth.email-verification-required:false}")
    private boolean emailVerificationRequired;

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
        // Anti-énumération (light) : message générique SANS renvoyer la valeur (email/téléphone),
        // pour ne pas confirmer l'existence d'un compte via le contenu de l'erreur. (La réponse
        // 409 révèle encore l'existence par le statut — l'uniformisation complète 201+email est
        // une décision UX différée.)
        if (repository.existsByEmailIgnoreCase(dto.email())) {
            throw new ConflictException("Cet email est déjà associé à un compte.");
        }
        if (dto.phone() != null && repository.existsByPhone(dto.phone())) {
            throw new ConflictException("Ce numéro de téléphone est déjà associé à un compte.");
        }
        Role role = roleRepository.findById(dto.roleId())
            .orElseThrow(() -> new NotFoundException("Role", dto.roleId()));

        UUID userId = UUID.randomUUID();
        User user = new User(
            userId,
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
        // Code de parrainage public stable (8 chars hex uppercase dérivés de l'UUID) — MÊME
        // formule que V14 (backfill) + generate_referral_code(). Sans ça, tout user créé APRÈS
        // V14 aurait referral_code = NULL → page parrainage cassée (code factice côté front).
        user.setReferralCode(userId.toString().replace("-", "").substring(0, 8).toUpperCase());
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

    /**
     * Inscription PUBLIQUE (POST /api/users/register, permitAll).
     * Force le rôle CLIENT côté serveur — un visiteur anonyme ne peut pas
     * s'auto-attribuer un rôle privilégié. Délègue ensuite à {@link #create}.
     *
     * <p><b>Reprise d'une inscription non confirmée (bug « ce numéro est déjà utilisé » au re-submit).</b>
     * Un compte resté {@code pending_email_verification} (créé mais jamais confirmé par OTP) ne doit pas
     * bloquer une nouvelle soumission du MÊME visiteur qui corrige une faute de saisie. On RÉUTILISE alors
     * ce compte ({@link #reusablePendingSignup} + {@link #updatePendingSignup}) en conservant {@code id} +
     * {@code referralCode} (le QR déjà affiché reste valable).
     *
     * <p><b>Sécurité — endpoint anonyme.</b> La reprise est ancrée EXCLUSIVEMENT sur l'EMAIL (jamais sur le
     * seul téléphone) et scopée au tenant. On ne réécrit JAMAIS l'email ni le mot de passe d'un compte existant :
     * ces deux champs sont les credentials, et l'OTP de vérification part vers l'email enregistré — laisser un
     * appelant anonyme les réécrire permettrait la prise de contrôle d'un compte pending appartenant à autrui
     * (attaquant fournissant l'email/le téléphone d'une victime). On ne met à jour que des champs de profil
     * corrigeables (nom, langue, téléphone — avec re-contrôle d'unicité). Cas non repris (email {@code active},
     * conflit de téléphone avec un autre compte, faute de frappe dans l'email lui-même) → {@link #create} lève
     * le 409 attendu ; les pending réellement abandonnés sont purgés par {@code PendingSignupPurgeJob}.
     */
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_USERS_BY_EMAIL, allEntries = true)
    public UserDto register(UserRegisterDto dto) {
        // Reprise d'une inscription non confirmée (ancrée sur l'email + tenant) au lieu de rejeter.
        User reusable = reusablePendingSignup(dto);
        if (reusable != null) {
            return updatePendingSignup(reusable, dto);
        }

        Role client = roleRepository.findByCode("CLIENT")
            .orElseThrow(() -> new NotFoundException("Role", "CLIENT"));
        UserDto created = create(new UserCreateDto(
            dto.tenantId(), client.getId(), dto.email(), dto.phone(),
            dto.password(), dto.firstName(), dto.lastName(), dto.language()));

        // Mutations post-création regroupées en un seul fetch/save :
        //  - RGPD : trace du consentement CGU (optionnel, compat ascendante — seulement si cguAccepted=true).
        //  - P1 enrollment : si la vérification email est requise, on bascule en pending_email_verification
        //    pour gater le login jusqu'à validation de l'OTP. Le statut renvoyé dans la réponse sert de
        //    signal au client (status=pending_email_verification → afficher l'écran OTP).
        boolean recordCgu = Boolean.TRUE.equals(dto.cguAccepted());
        if (recordCgu || emailVerificationRequired) {
            User u = repository.findById(created.id()).orElse(null);
            if (u != null) {
                if (recordCgu) u.setCguAcceptedAt(Instant.now());
                if (emailVerificationRequired) u.setStatus(User.STATUS_PENDING_EMAIL_VERIFICATION);
                repository.save(u);
                created = u.toDto();  // reflète le statut pending dans la réponse de register
            }
        }
        return created;
    }

    /**
     * Renvoie le compte {@code pending_email_verification} RÉUTILISABLE pour cette inscription, sinon {@code null}.
     *
     * <p><b>Ancre = EMAIL uniquement</b> (l'email est UNIQUE global et c'est la cible de livraison de l'OTP :
     * seul son propriétaire peut activer le compte). Réutilisable ⟺ un compte existe pour cet email, est encore
     * {@code pending_email_verification}, non supprimé, ET appartient au MÊME tenant que la demande. Tous les
     * autres cas → {@code null} et le flux normal ({@link #register} → {@link #create}) s'applique (création,
     * ou 409 si email/téléphone déjà pris). On ne réutilise JAMAIS sur un simple match de téléphone : sinon un
     * appelant anonyme fournissant le téléphone d'une victime pourrait faire réécrire le compte de celle-ci.
     *
     * <p>NB : {@code findByEmailIgnoreCase} ne filtre PAS le soft-delete ({@link User} n'a pas de
     * {@code @SQLRestriction}) → on écarte explicitement {@code deletedAt != null} ici.
     */
    private User reusablePendingSignup(UserRegisterDto dto) {
        User byEmail = repository.findByEmailIgnoreCase(dto.email().toLowerCase())
            .filter(u -> u.getDeletedAt() == null)
            .orElse(null);
        if (byEmail == null) return null;                                             // email libre → create()
        if (!User.STATUS_PENDING_EMAIL_VERIFICATION.equals(byEmail.getStatus())) {
            return null;                                                              // compte actif → create() → 409
        }
        // L'email étant unique global, on ne réutilise pas au travers d'une frontière de tenant.
        UUID existingTenantId = byEmail.getTenant() != null ? byEmail.getTenant().getId() : null;
        if (!java.util.Objects.equals(existingTenantId, dto.tenantId())) return null; // autre tenant → create()
        return byEmail;
    }

    /**
     * Reprend un compte pending identifié par son EMAIL : met à jour uniquement les champs de profil
     * corrigeables (prénom, nom, langue, téléphone), en conservant {@code id} + {@code referralCode} +
     * date de création (le QR déjà montré reste valable) et le statut {@code pending_email_verification}.
     *
     * <p><b>On ne réécrit NI l'email NI le mot de passe</b> (credentials) : l'email est l'ancre (inchangé) et
     * réécrire le hash depuis un endpoint anonyme ouvrirait une prise de contrôle. Le téléphone n'est mis à
     * jour qu'après re-contrôle d'unicité (message générique, anti-énumération). NE republie PAS
     * {@link UserRegisteredEvent} — déjà émis à la 1re inscription (évite double welcome / enrollment / analytics).
     */
    private UserDto updatePendingSignup(User u, UserRegisterDto dto) {
        if (dto.phone() != null && !dto.phone().equals(u.getPhone())) {
            // existsByPhone couvre toutes les lignes (dont soft-deleted) → 409 propre AVANT le save (pas d'erreur DB brute).
            if (repository.existsByPhone(dto.phone())) {
                throw new ConflictException("Ce numéro de téléphone est déjà associé à un compte.");
            }
            u.setPhone(dto.phone());
        }
        u.setFirstName(dto.firstName());
        u.setLastName(dto.lastName());
        if (dto.language() != null) u.setLanguage(dto.language());
        if (Boolean.TRUE.equals(dto.cguAccepted())) u.setCguAcceptedAt(Instant.now());
        return repository.save(u).toDto();
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
        if (dto.city() != null) user.setCity(dto.city());
        if (dto.allergens() != null) user.setAllergens(dto.allergens().toArray(new String[0]));
        if (dto.language() != null) user.setLanguage(dto.language());
        return repository.save(user).toDto();
    }

    /**
     * H — met à jour le type de membre PCC d'un user (admin only via le controller).
     * {@code null} retire le statut ; vocabulaire {@code resident|non_resident} (sinon 400).
     * Pas d'éviction cache : le type de membre n'entre pas dans les authorities/login.
     */
    @Transactional
    public UserDto updatePccMemberType(UUID id, String memberType) {
        if (memberType != null && !"resident".equals(memberType) && !"non_resident".equals(memberType)) {
            throw new BadRequestException("Type de membre PCC invalide : " + memberType);
        }
        User user = repository.findById(id)
            .filter(u -> u.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("User", id));
        user.setPccMemberType(memberType);
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

    /**
     * Suppression self-service du PROPRE compte ({@code DELETE /api/users/me}).
     *
     * <p>Conformité App Store §5.1.1(v) (« suppression de compte in-app, permanente, pas une
     * simple désactivation ») + RGPD « droit à l'effacement ». On <b>anonymise</b> les PII et on
     * libère l'email/téléphone (uniques) pour une ré-inscription, tout en gardant la ligne
     * (intégrité FK des données historiques : réservations, points). Le user devient inaccessible
     * (soft-delete + cache évincé) et ses sessions/push sont révoqués via {@link AccountDeletedEvent}
     * (frontière Modulith : aucun appel direct vers auth/notification).
     */
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_USERS_BY_EMAIL, allEntries = true)
    public void deleteOwnAccount(UUID id) {
        User user = repository.findById(id)
            .filter(u -> u.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("User", id));
        // Anonymisation PII : strip + tombstone unique (email/téléphone libérés).
        user.setEmail("deleted-" + id + "@deleted.oneclick");
        user.setPhone(null);
        user.setFirstName("Compte");
        user.setLastName("supprimé");
        user.setAvatarUrl(null);
        user.setStatus("deleted");
        user.markDeleted();
        repository.save(user);
        // Purge cache userDetails : un JWT déjà émis ne doit plus réauthentifier (0 autorité).
        userDetailsService.evictUser(id);
        // Cross-module (events Modulith) : auth → révoque refresh tokens ; notification → purge device tokens.
        eventPublisher.publishEvent(new AccountDeletedEvent(id, Instant.now()));
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
        // BE-3 — l'utilisateur a défini son propre mot de passe : le drapeau « changement forcé »
        // (posé au provisioning avec mdp temporaire) tombe. No-op pour un compte normal (déjà false).
        user.setPasswordMustChange(false);
        repository.save(user);
        // Bug 34 — purge UserDetails cache : le hash ayant changé, le payload
        // sérialisé Redis devient stale et empêcherait une réauthentification
        // immédiate avec le nouveau password si un autre flow (BasicAuth, etc.)
        // tape le {@code passwordHash} via {@code UserDetails.getPassword()}.
        userDetailsService.evictUser(id);
    }

    /**
     * Réinitialisation du mot de passe SANS mot de passe actuel — flow « mot de passe oublié »
     * (Phase A enrollment). L'identité de l'appelant est prouvée en amont par l'OTP email
     * (purpose=reset_password) vérifié dans {@code AuthService.resetPassword} ; ce service ne
     * gère que le stockage (BCrypt) + l'éviction de cache, comme {@link #changePassword}.
     *
     * <p>Refuse la réutilisation du mot de passe courant (même garde que changePassword).
     * Même cache evict ({@code userDetails}) pour invalider tout payload sérialisé stale.</p>
     */
    @Transactional
    public void resetPassword(UUID id, String newPassword) {
        User user = repository.findById(id)
            .filter(u -> u.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("User", id));

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BadRequestException("Le nouveau mot de passe doit être différent de l'ancien");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // BE-3 — cohérent avec changePassword : un reset effectif lève aussi le « changement forcé ».
        user.setPasswordMustChange(false);
        repository.save(user);
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

        // Slug du tenant (additif au tenantId UUID) : accès LAZY autorisé en session
        // (@Transactional readOnly). null pour un user global (admin plateforme, tenant null).
        // Sert au gating UI par slug côté clients multi-tenant (ex. app Store staff) — la
        // sécurité reste serveur (ABAC par JWT).
        Tenant tenant = u.getTenant();
        String tenantSlug = tenant != null ? tenant.getSlug() : null;

        return new MeContextDto(
            new MeContextDto.UserSummary(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(),
                u.getPhone(), u.getAvatarUrl(), u.getCity(),
                u.getAllergens() == null ? List.of() : java.util.Arrays.asList(u.getAllergens()),
                u.getLanguage(), u.getStatus(), u.getTenantId(), tenantSlug,
                u.isPasswordMustChange()),
            role != null ? new MeContextDto.RoleSummary(role.getCode(), role.getName()) : null,
            menus,
            List.copyOf(permissions)
        );
    }
}
