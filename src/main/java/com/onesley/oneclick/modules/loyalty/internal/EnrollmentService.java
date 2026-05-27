package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.core.identity.api.Role;
import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserRepository;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.loyalty.api.EnrollLookupResultDto;
import com.onesley.oneclick.modules.loyalty.api.EnrollMemberDto;
import com.onesley.oneclick.modules.loyalty.api.EnrollMemberResultDto;
import com.onesley.oneclick.modules.loyalty.api.EnrollmentRecordDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyEarnDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyTransactionDto;
import com.onesley.oneclick.security.SecurityHelper;
import static com.onesley.oneclick.shared.Temporals.toInstant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

/**
 * Service orchestrateur "Inscrire membre" — port commit legacy e7a8b49b.
 *
 * <p><b>Pattern Modulith CLOSED</b> : ce service vit dans {@code loyalty/internal}
 * (l'enrollment EST une opération loyalty, ce module est l'owner du concept
 * "welcome points"). Il orchestre 3 modules :
 * <ul>
 *   <li>{@code core.identity} — lookup ({@link UserRepository}) ou création de user</li>
 *   <li>{@code modules.restaurant} — vérification staff actif via SQL natif
 *       (les services {@code RestaurantSubResourceService} sont {@code internal},
 *       on passe par la DB pour éviter une dépendance cross-module forbidden)</li>
 *   <li>{@code modules.loyalty} (propre) — {@link GainRuleRepository} pour le
 *       plafond + {@link LoyaltyService#earnPoints} pour le crédit</li>
 * </ul>
 *
 * <p><b>Flow</b> :
 * <ol>
 *   <li>RBAC : caller authentifié + (SUPERADMIN OU staff actif du restaurant)</li>
 *   <li>Identification : si {@code clientId} fourni → fetch ; sinon lookup par
 *       email/phone ; sinon création silencieuse (random password hash)</li>
 *   <li>Validation plafond : {@code welcomePoints <= gain_rules.welcome_points_max}</li>
 *   <li>Crédit : delegate à {@link LoyaltyService#earnPoints} avec
 *       {@code reason="welcome"} → crée account + transaction + event Kafka</li>
 *   <li>(TODO V2) Email d'invitation via {@code SMTPService} si {@code sendInvite=true}</li>
 * </ol>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EnrollmentService {

    /** Role CLIENT — UUID stable défini dans V1 (cf docker DB \du roles). */
    private static final UUID CLIENT_ROLE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    private final UserRepository userRepository;
    private final GainRuleRepository gainRuleRepository;
    private final LoyaltyService loyaltyService;
    private final PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager em;

    // ─── ENROLLMENT ────────────────────────────────────────────────────────

    @Transactional
    public EnrollMemberResultDto enrollMember(EnrollMemberDto dto) {
        UUID callerId = SecurityHelper.currentUserId();
        if (callerId == null) {
            throw new ForbiddenException("Non authentifié");
        }

        // 1. RBAC : SUPERADMIN OU staff actif du restaurant
        boolean isAdmin = SecurityHelper.hasRole("SUPERADMIN");
        if (!isAdmin && !isActiveStaff(callerId, dto.restaurantId())) {
            throw new ForbiddenException(
                "Accès refusé : vous n'êtes pas staff actif de ce restaurant"
            );
        }

        // 2. Récupération règle pour plafond welcome_points_max
        GainRule rule = gainRuleRepository.findByRestaurantIdAndDeletedAtIsNull(dto.restaurantId())
            .orElseThrow(() -> new NotFoundException(
                "GainRule non configurée pour le restaurant — créer une règle d'abord"
            ));

        int requested = dto.welcomePoints();
        if (requested > rule.getWelcomePointsMax()) {
            throw new BadRequestException(String.format(
                "welcomePoints (%d) dépasse le plafond welcome_points_max (%d) du restaurant",
                requested, rule.getWelcomePointsMax()
            ));
        }

        // 3. Identification du client (existant OU création)
        boolean isNewUser = false;
        UUID clientId = dto.clientId();
        if (clientId == null) {
            Optional<User> existing = lookupExistingUser(dto.email(), dto.phone());
            if (existing.isPresent()) {
                clientId = existing.get().getId();
            } else {
                clientId = createClientUser(dto);
                isNewUser = true;
            }
        } else {
            // Vérifie que l'user existe
            final UUID providedId = clientId;
            userRepository.findById(providedId)
                .orElseThrow(() -> new NotFoundException("User", providedId));
        }

        // 4. Crédit via LoyaltyService (events Kafka publish inclus)
        LoyaltyTransactionDto tx;
        if (requested > 0) {
            tx = loyaltyService.earnPoints(new LoyaltyEarnDto(
                clientId, dto.restaurantId(), requested, null, "welcome"
            ));
        } else {
            // Cas welcomePoints=0 : on crée juste le compte sans transaction
            tx = null;
        }

        // 5. TODO V2 : SMTPService.sendInvite si dto.sendInvite() == true
        boolean inviteSent = false;
        if (Boolean.TRUE.equals(dto.sendInvite())) {
            log.info("[enroll] invite email TODO V2 — clientId={} restaurantId={}",
                clientId, dto.restaurantId());
        }

        log.info("[enroll] member {} restaurantId={} points={} newUser={}",
            clientId, dto.restaurantId(), requested, isNewUser);

        return new EnrollMemberResultDto(
            clientId,
            isNewUser,
            tx != null ? tx.points() : 0,
            tx != null ? tx.accountId() : null,
            tx != null ? tx.id() : null,
            inviteSent
        );
    }

    // ─── LIST RECENT ENROLLMENTS ──────────────────────────────────────────

    /**
     * Liste les N dernières inscriptions welcome d'un restaurant (UI : panneau
     * sous le wizard). Source : {@code loyalty_transactions WHERE reason='welcome'}
     * joint avec {@code loyalty_accounts} + {@code users}. SQL natif car
     * cross-module (loyalty + identity) et besoin d'un projection custom.
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<EnrollmentRecordDto> listRecentEnrollments(UUID restaurantId, int limit) {
        UUID callerId = SecurityHelper.currentUserId();
        if (callerId == null) throw new ForbiddenException("Non authentifié");
        if (!SecurityHelper.hasRole("SUPERADMIN") && !isActiveStaff(callerId, restaurantId)) {
            throw new ForbiddenException("Accès refusé");
        }

        List<Object[]> rows = em.createNativeQuery("""
            SELECT t.id, u.id, u.first_name, u.last_name, u.email,
                   t.points, t.created_at
              FROM loyalty_transactions t
              JOIN loyalty_accounts a ON a.id = t.account_id
              JOIN users u ON u.id = a.client_id
             WHERE a.restaurant_id = :restaurantId
               AND t.reason = 'welcome'
             ORDER BY t.created_at DESC
             LIMIT :limit
            """)
            .setParameter("restaurantId", restaurantId)
            .setParameter("limit", Math.max(1, Math.min(limit, 100)))
            .getResultList();

        List<EnrollmentRecordDto> result = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            result.add(new EnrollmentRecordDto(
                (UUID) r[0], (UUID) r[1],
                (String) r[2], (String) r[3], (String) r[4],
                ((Number) r[5]).intValue(),
                toInstant(r[6])
            ));
        }
        return result;
    }

    /**
     * Bug 33 — Convertit la valeur Timestamp/OffsetDateTime renvoyée par
     * PostgreSQL JDBC en {@link java.time.Instant}. Le driver moderne (PG JDBC
     * 42.7+) retourne {@code OffsetDateTime} pour {@code timestamp with time zone},
     * pas {@code java.sql.Timestamp} comme historiquement — le cast direct
     * provoquait un ClassCastException → 500. Pattern partagé avec
     * {@link LoyaltyExtensionService}, {@link com.onesley.oneclick.modules.analytics.internal.AdminViewsService},
     * {@link com.onesley.oneclick.modules.oneclickhi.internal.OneClickHIService}.
     */
    // ─── HELPERS PRIVÉS ───────────────────────────────────────────────────

    /**
     * Vérifie staff actif via SQL natif (cross-module restaurant_staffs).
     * Pas d'injection RestaurantSubResourceService (interdit en Modulith CLOSED).
     */
    private boolean isActiveStaff(UUID userId, UUID restaurantId) {
        Number count = (Number) em.createNativeQuery("""
            SELECT COUNT(*) FROM restaurant_staffs
             WHERE user_id = :userId
               AND restaurant_id = :restaurantId
               AND deleted_at IS NULL
            """)
            .setParameter("userId", userId)
            .setParameter("restaurantId", restaurantId)
            .getSingleResult();
        return count.longValue() > 0;
    }

    /**
     * Recherche un client existant par email OU téléphone pour le flow « Inscrire
     * membre » (staff resto). Réutilise {@link #lookupExistingUser} et ne renvoie
     * que l'identité minimale ({@link EnrollLookupResultDto}). 404 si introuvable
     * (le front bascule alors sur l'étape « Créer le compte »). RBAC CREATE:LOYALTY
     * appliqué au controller — staff/admin uniquement, jamais le CLIENT.
     */
    public EnrollLookupResultDto lookupClient(String email, String phone) {
        User u = lookupExistingUser(email, phone)
            .orElseThrow(() -> new NotFoundException("User", email != null && !email.isBlank() ? email : phone));
        return new EnrollLookupResultDto(u.getId(), u.getFirstName(), u.getLastName());
    }

    private Optional<User> lookupExistingUser(String email, String phone) {
        if (email != null && !email.isBlank()) {
            Optional<User> byEmail = userRepository.findByEmailIgnoreCase(email)
                .filter(u -> u.getDeletedAt() == null);
            if (byEmail.isPresent()) return byEmail;
        }
        if (phone != null && !phone.isBlank()) {
            return userRepository.findByPhone(phone)
                .filter(u -> u.getDeletedAt() == null);
        }
        return Optional.empty();
    }

    /**
     * Crée un nouveau user CLIENT lié au tenant du restaurant. Password aléatoire
     * (le futur membre devra utiliser "Mot de passe oublié" pour activer, OU le
     * V2 SMTP enverra un magic link).
     */
    private UUID createClientUser(EnrollMemberDto dto) {
        if (dto.email() == null || dto.email().isBlank()) {
            throw new BadRequestException(
                "email requis pour créer un nouveau membre (ou fournir clientId)"
            );
        }
        if (dto.firstName() == null || dto.firstName().isBlank()
            || dto.lastName() == null || dto.lastName().isBlank()) {
            throw new BadRequestException(
                "firstName et lastName requis pour créer un nouveau membre"
            );
        }

        // Tenant du restaurant (héritage tenant_id)
        UUID restaurantTenantId = (UUID) em.createNativeQuery(
            "SELECT tenant_id FROM restaurants WHERE id = :id"
        ).setParameter("id", dto.restaurantId()).getSingleResult();

        Role clientRole = em.getReference(Role.class, CLIENT_ROLE_ID);
        String randomPassword = generateRandomPassword();
        String hash = passwordEncoder.encode(randomPassword);

        User newUser = new User(
            UUID.randomUUID(), clientRole,
            dto.email().toLowerCase().trim(), hash,
            dto.firstName().trim(), dto.lastName().trim()
        );
        if (dto.phone() != null && !dto.phone().isBlank()) {
            newUser.setPhone(dto.phone().trim());
        }
        if (restaurantTenantId != null) {
            Tenant tenantRef = em.getReference(Tenant.class, restaurantTenantId);
            newUser.setTenant(tenantRef);
        }
        userRepository.save(newUser);
        log.info("[enroll] créé user clientId={} email={} tenant={}",
            newUser.getId(), newUser.getEmail(), restaurantTenantId);
        return newUser.getId();
    }

    private String generateRandomPassword() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
