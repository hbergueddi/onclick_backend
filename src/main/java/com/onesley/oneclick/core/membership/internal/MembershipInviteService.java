package com.onesley.oneclick.core.membership.internal;

import com.onesley.oneclick.core.identity.api.Role;
import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.core.identity.api.UserRepository;
import com.onesley.oneclick.core.membership.api.InviteMemberDto;
import com.onesley.oneclick.core.membership.api.MembershipDto;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.shared.events.MemberEnrollmentRequestedEvent;
import com.onesley.oneclick.shared.events.MembershipActivatedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service d'invitation d'un membre dans un programme (P2 — « l'admin du tenant invite »).
 *
 * <p><b>Modèle (invariants P0/P1)</b> : il n'y a qu'UN compte OneClick par personne. L'invitation
 * <b>réutilise</b> le compte existant (email/téléphone) sinon le <b>crée</b> en tenant home
 * {@code oneclick}, puis <b>ajoute une membership</b> (rôle programme {@code MEMBER}, statut
 * {@code active}). Jamais de 2ᵉ compte. Les autorités programme du membre découlent du pliage de
 * sa membership (P1) — donc on évince son cache {@code userDetails} via un event.</p>
 *
 * <p><b>Sécurité</b> :
 * <ul>
 *   <li>RBAC : {@code hasAuthority('CREATE:MEMBERSHIPS')} (au controller).</li>
 *   <li>ABAC own-tenant : un admin n'invite que dans SON tenant home — sauf SUPERADMIN
 *       (bypass via {@code DELETE:TENANTS}, autorité exclusive plateforme).</li>
 * </ul>
 * On lit le contexte de sécurité via l'API Spring ({@code SecurityContextHolder}) et NON via notre
 * {@code SecurityHelper} : le module {@code security} dépend déjà de {@code membership.api} (pliage),
 * donc {@code membership → security} créerait un cycle Modulith. Mêmes raisons pour l'éviction de
 * cache, faite par event ({@link MembershipActivatedEvent}) consommé côté {@code security}.</p>
 *
 * <p><b>Création de compte cross-module</b> : via les contrats {@code identity.api}
 * ({@link UserRepository}, {@link Role}) + {@code tenant.api} ({@link Tenant}) — même approche que
 * {@code EnrollmentService} (loyalty). Aucun import {@code internal} d'un autre module.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MembershipInviteService {

    /** Rôle CLIENT — compte OneClick de base (V1). */
    private static final UUID CLIENT_ROLE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    /** Rôle programme MEMBER — porte les autorités programme pliées par membership (V91). */
    private static final UUID MEMBER_ROLE_ID = UUID.fromString("10000000-0000-0000-0000-000000000006");
    /** Autorité EXCLUSIVE SUPERADMIN (vérifiée en DB) → bypass de l'ABAC own-tenant. */
    private static final String PLATFORM_ADMIN_AUTHORITY = "DELETE:TENANTS";

    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final UserDirectoryApi userDirectory;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public MembershipDto invite(UUID tenantId, InviteMemberDto dto) {
        UUID caller = MembershipSecurityContext.currentUserId();
        if (caller == null) {
            throw new ForbiddenException("Non authentifié");
        }

        // ─── ABAC own-tenant (sauf plateforme/SUPERADMIN) ──────────────────────
        if (!MembershipSecurityContext.hasAuthority(PLATFORM_ADMIN_AUTHORITY)) {
            UUID callerHomeTenant = userDirectory.tenantIdById(caller).orElse(null);
            if (!tenantId.equals(callerHomeTenant)) {
                throw new ForbiddenException(
                    "Accès refusé : vous ne pouvez inviter des membres que dans votre propre tenant");
            }
        }

        // Tenant cible (slug/name pour brander l'email d'activation) — 404 si inconnu.
        TenantInfo tenant = resolveTenant(tenantId);

        // ─── 1. Compte OneClick : réutilise sinon crée (home = oneclick) ────────
        boolean newAccount = false;
        User user = lookupExistingUser(dto.email(), dto.phone()).orElse(null);
        if (user == null) {
            user = createOneClickClient(dto);
            newAccount = true;
        }
        UUID userId = user.getId();

        // ─── 2. Membership : crée OU réactive ; idempotent si déjà active ──────
        TenantMembership membership =
            membershipRepository.findByUserIdAndTenantIdAndDeletedAtIsNull(userId, tenantId).orElse(null);
        boolean changed;
        if (membership == null) {
            membership = new TenantMembership(
                UUID.randomUUID(), userId, tenantId, dto.memberType(), MEMBER_ROLE_ID,
                TenantMembership.STATUS_ACTIVE, caller, Instant.now());
            membershipRepository.save(membership);
            changed = true;
        } else if (!TenantMembership.STATUS_ACTIVE.equals(membership.getStatus())) {
            // invited / revoked (deleted_at null) → (ré)active
            membership.setStatus(TenantMembership.STATUS_ACTIVE);
            if (membership.getJoinedAt() == null) membership.setJoinedAt(Instant.now());
            if (membership.getRoleId() == null) membership.setRoleId(MEMBER_ROLE_ID);
            if (dto.memberType() != null) membership.setMemberType(dto.memberType());
            membershipRepository.save(membership);
            changed = true;
        } else {
            changed = false; // déjà membre actif → totalement idempotent
        }

        // ─── 3. Events (après changement seulement) ────────────────────────────
        if (changed) {
            // Éviction du cache userDetails (autorités programme du membre changées).
            events.publishEvent(new MembershipActivatedEvent(userId, tenantId, Instant.now()));
            // Compte fraîchement créé → lien d'activation brandé (réutilise le pipeline
            // MemberEnrollmentRequestedEvent → core.auth → core.email). Un compte existant a
            // déjà ses identifiants (anti-takeover) → pas d'email d'activation.
            if (newAccount) {
                events.publishEvent(new MemberEnrollmentRequestedEvent(
                    userId, user.getEmail(), user.getFirstName(),
                    tenantId, tenant.slug(), tenant.name(), caller, Instant.now()));
            }
        }

        log.info("[membership/invite] tenant={} user={} newAccount={} changed={} by={}",
            tenantId, userId, newAccount, changed, caller);

        return new MembershipDto(
            membership.getId(), userId, tenantId,
            membership.getMemberType(), membership.getStatus(), newAccount);
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────────

    private Optional<User> lookupExistingUser(String email, String phone) {
        if (email != null && !email.isBlank()) {
            Optional<User> byEmail = userRepository.findByEmailIgnoreCase(email.trim())
                .filter(u -> u.getDeletedAt() == null);
            if (byEmail.isPresent()) return byEmail;
        }
        if (phone != null && !phone.isBlank()) {
            return userRepository.findByPhone(phone.trim()).filter(u -> u.getDeletedAt() == null);
        }
        return Optional.empty();
    }

    /** Crée un compte CLIENT OneClick (tenant home = {@code oneclick}). Mot de passe aléatoire
     *  serveur → activé par le lien magique (email) ou « mot de passe oublié ». */
    private User createOneClickClient(InviteMemberDto dto) {
        if (dto.firstName() == null || dto.firstName().isBlank()
            || dto.lastName() == null || dto.lastName().isBlank()) {
            throw new BadRequestException(
                "firstName et lastName requis pour créer un nouveau membre (compte inexistant)");
        }
        UUID oneclickTenantId = resolveOneClickTenantId();
        Role clientRole = em.getReference(Role.class, CLIENT_ROLE_ID);
        String hash = passwordEncoder.encode(generateRandomPassword());

        User u = new User(
            UUID.randomUUID(), clientRole,
            dto.email().toLowerCase().trim(), hash,
            dto.firstName().trim(), dto.lastName().trim());
        if (dto.phone() != null && !dto.phone().isBlank()) {
            u.setPhone(dto.phone().trim());
        }
        u.setTenant(em.getReference(Tenant.class, oneclickTenantId));
        // referral_code stable (même formule que UserService.create — sinon page parrainage cassée).
        u.setReferralCode(u.getId().toString().replace("-", "").substring(0, 8).toUpperCase());
        userRepository.save(u);
        log.info("[membership/invite] compte OneClick créé user={} email={} (home=oneclick)",
            u.getId(), u.getEmail());
        return u;
    }

    private UUID resolveOneClickTenantId() {
        try {
            return (UUID) em.createNativeQuery("SELECT id FROM tenants WHERE slug = 'oneclick'")
                .getSingleResult();
        } catch (RuntimeException e) {
            throw new NotFoundException("Tenant", "oneclick");
        }
    }

    /** slug/name du tenant cible — branding email. 404 si le tenant n'existe pas. */
    @SuppressWarnings("unchecked")
    private TenantInfo resolveTenant(UUID tenantId) {
        List<Object[]> rows = em.createNativeQuery(
                "SELECT slug, name FROM tenants WHERE id = :id")
            .setParameter("id", tenantId)
            .getResultList();
        if (rows.isEmpty()) {
            throw new NotFoundException("Tenant", tenantId);
        }
        Object[] r = rows.get(0);
        return new TenantInfo((String) r[0], (String) r[1]);
    }

    private record TenantInfo(String slug, String name) {}

    private String generateRandomPassword() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
