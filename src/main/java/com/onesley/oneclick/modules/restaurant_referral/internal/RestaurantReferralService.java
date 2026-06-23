package com.onesley.oneclick.modules.restaurant_referral.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.core.tenant.api.TenantDirectoryApi;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.events.RestaurantReferralActivatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.onesley.oneclick.modules.restaurant_referral.api.RestaurantReferralDtos.RestaurantReferralDto;

/**
 * Service du parrainage RESTAURANT-à-RESTAURANT (owner → owner) — V100.
 *
 * <p><b>Spec métier validée</b> : owner → owner ; récompense = POINTS au PARRAIN seul, à
 * l'activation (immédiat) ; scope mono-tenant {@code oneclick} ; pas de plafond. Aucun flux
 * financier.
 *
 * <h3>Sécurité — défense en profondeur</h3>
 * <ul>
 *   <li>RBAC grossier : {@code @PreAuthorize hasAuthority('VERB:RESTAURANT_REFERRAL')} sur le
 *       contrôleur (jamais {@code hasRole}).</li>
 *   <li>ABAC fin (ici) : l'appelant doit être OWNER ACTIF du resto concerné (parrain pour
 *       {@code getOrCreateMyCode}/{@code listMine} ; filleul pour {@code activate}) — ou admin.</li>
 * </ul>
 *
 * <p>Crédit des points : on NE touche PAS le module {@code loyalty} directement. À l'activation, on
 * publie {@link RestaurantReferralActivatedEvent} ; le listener loyalty
 * ({@code RestaurantReferralRewardListener}) crédite le parrain (frontière Modulith).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RestaurantReferralService {

    /** Slug du tenant public — seul périmètre autorisé pour ce parrainage (mono-tenant). */
    static final String PUBLIC_TENANT_SLUG = "oneclick";

    /** Préfixe des codes générés. */
    private static final String CODE_PREFIX = "RR-";
    /** Alphabet des codes (sans I/O/0/1 pour la lisibilité). */
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_BODY_LEN = 6;
    private static final int CODE_MAX_TRIES = 10;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Récompense (points fidélité) créditée au PARRAIN à l'activation. Seule valeur non
     * explicitement spécifiée : fixée par défaut à 100, AJUSTABLE via la propriété
     * {@code app.restaurant-referral.reward-points} (config DB/feature-flag à terme).
     */
    @Value("${app.restaurant-referral.reward-points:100}")
    private int rewardPoints;

    private final RestaurantReferralRepository repository;
    private final TenantDirectoryApi tenantDirectory;
    private final UserDirectoryApi userDirectory;
    private final ApplicationEventPublisher eventPublisher;
    private final RestaurantReferralDashboardPublisher dashboardPublisher;
    private final Clock clock;

    // ─── Code parrain (owner du resto PARRAIN) ────────────────────────────────────────────────

    /**
     * L'owner obtient (ou crée à la volée) le code de parrainage de SON resto. Idempotent : un seul
     * code par resto parrain. ABAC owner-scope + scope tenant {@code oneclick}.
     */
    @Transactional
    public RestaurantReferralDto getOrCreateMyCode(UUID restaurantId) {
        UUID tenantId = requireOneClickRestaurant(restaurantId);
        UUID owner = requireOwnerOfOrAdmin(restaurantId);

        return repository.findByReferrerRestaurantId(restaurantId)
            .map(RestaurantReferral::toDto)
            .orElseGet(() -> {
                RestaurantReferral created = new RestaurantReferral(
                    UUID.randomUUID(), restaurantId, owner, generateUniqueCode(), tenantId);
                return repository.save(created).toDto();
            });
    }

    /** Parrainages ACTIVÉS portés par ce resto (owner-scope). */
    public List<RestaurantReferralDto> listMine(UUID restaurantId) {
        requireOneClickRestaurant(restaurantId);
        requireOwnerOfOrAdmin(restaurantId);
        return repository.findAllByReferrerRestaurantIdAndStatus(restaurantId, "activated").stream()
            .map(RestaurantReferral::toDto)
            .toList();
    }

    // ─── Activation (owner du resto FILLEUL) ──────────────────────────────────────────────────

    /**
     * Active un code de parrainage : l'owner du resto FILLEUL consomme le code du resto PARRAIN.
     * Déclenchement immédiat de la récompense (event → crédit points parrain).
     *
     * <p>Validations : code inconnu → 404 ; auto-parrainage (resto parrain == resto filleul) → 400 ;
     * code déjà activé OU resto filleul déjà parrainé → 409 ; resto filleul/parrain hors tenant
     * {@code oneclick} → 403.
     */
    @Transactional
    public RestaurantReferralDto activate(String code, UUID refereeRestaurantId) {
        String trimmed = code == null ? "" : code.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException("Code de parrainage requis");
        }
        // ABAC + scope : l'appelant doit être owner du resto FILLEUL, dans le tenant oneclick.
        requireOneClickRestaurant(refereeRestaurantId);
        UUID refereeOwner = requireOwnerOfOrAdmin(refereeRestaurantId);

        RestaurantReferral referral = repository.findByReferralCodeIgnoreCase(trimmed)
            .orElseThrow(() -> new NotFoundException("RestaurantReferral", trimmed));

        // Scope mono-tenant : le code parrain doit lui aussi vivre dans oneclick.
        if (!isOneClickTenant(referral.getTenantId())) {
            throw new ForbiddenException("Parrainage restreint au tenant OneClick");
        }
        // Self-referral : le resto filleul ne peut pas être le resto parrain.
        if (referral.getReferrerRestaurantId().equals(refereeRestaurantId)) {
            throw new BadRequestException("Un restaurant ne peut pas se parrainer lui-même");
        }
        // Double-activation : code déjà consommé, OU resto filleul déjà parrainé par ailleurs.
        if (referral.isActivated()) {
            throw new ConflictException("Ce code de parrainage a déjà été activé");
        }
        if (repository.existsByRefereeRestaurantId(refereeRestaurantId)) {
            throw new ConflictException("Ce restaurant a déjà été parrainé");
        }

        Instant now = Instant.now(clock);
        referral.activate(refereeRestaurantId, refereeOwner, rewardPoints, now);
        RestaurantReferralDto saved = repository.save(referral).toDto();

        // Lot B3 — destinataires admin (SUPERADMIN) résolus ICI (le module a la dépendance
        // core.identity), portés sur l'event. Pattern ContractExpiringSoonEvent : core.notification
        // reste sans dépendance identity et n'a qu'à itérer pour créer les notifs.
        List<UUID> admins = userDirectory.adminUserIds();

        // Récompense = points au PARRAIN seul, via event → module loyalty (frontière Modulith) ;
        // + notif aux admins plateforme via core.notification (Lot B3).
        eventPublisher.publishEvent(new RestaurantReferralActivatedEvent(
            saved.id(), saved.referrerUserId(), saved.referrerRestaurantId(),
            saved.rewardPoints(), saved.tenantId(), admins, now));

        // Temps réel : pousse l'empreinte au dashboard admin (best-effort, jamais bloquant).
        dashboardPublisher.pushNow();
        return saved;
    }

    // ─── Helpers ABAC / scope ─────────────────────────────────────────────────────────────────

    /**
     * Vérifie que le resto existe et appartient au tenant {@code oneclick} ; renvoie son tenant_id.
     * 404 si resto inconnu/supprimé, 403 si hors tenant {@code oneclick}.
     */
    private UUID requireOneClickRestaurant(UUID restaurantId) {
        if (restaurantId == null) {
            throw new BadRequestException("restaurantId obligatoire");
        }
        UUID tenantId = repository.findTenantIdOfRestaurant(restaurantId)
            .orElseThrow(() -> new NotFoundException("Restaurant", restaurantId));
        if (!isOneClickTenant(tenantId)) {
            throw new ForbiddenException("Parrainage restreint au tenant OneClick");
        }
        return tenantId;
    }

    private boolean isOneClickTenant(UUID tenantId) {
        return tenantId != null
            && PUBLIC_TENANT_SLUG.equals(tenantDirectory.slugById(tenantId).orElse(null));
    }

    /**
     * Exige que l'appelant soit OWNER ACTIF du resto (ou admin) ; renvoie l'id de l'owner réel du
     * resto (pour les admins qui agissent au nom du resto). 403 sinon.
     */
    private UUID requireOwnerOfOrAdmin(UUID restaurantId) {
        UUID current = SecurityHelper.currentUserId();
        if (current == null) {
            throw new ForbiddenException("Authentification requise");
        }
        if (!SecurityHelper.isAdmin() && !repository.isOwnerOfRestaurant(current, restaurantId)) {
            throw new ForbiddenException("Accès refusé : vous n'êtes pas owner de ce restaurant");
        }
        // Owner réel du resto = porteur de la récompense (un admin agit pour le compte du resto).
        if (repository.isOwnerOfRestaurant(current, restaurantId)) {
            return current;
        }
        return resolveOwnerOf(restaurantId);
    }

    /** Owner actif du resto (cas admin) — 422 si le resto n'a pas d'owner actif. */
    private UUID resolveOwnerOf(UUID restaurantId) {
        return repository.findOwnerUserIdOf(restaurantId)
            .orElseThrow(() -> new ConflictException(
                "Aucun owner actif pour ce restaurant — parrainage impossible"));
    }

    /** Génère un code unique {@code RR-XXXXXX} (anti-collision borné). */
    private String generateUniqueCode() {
        for (int i = 0; i < CODE_MAX_TRIES; i++) {
            String code = randomCode();
            if (!repository.findByReferralCodeIgnoreCase(code).isPresent()) {
                return code;
            }
        }
        throw new IllegalStateException("Impossible de générer un code de parrainage unique");
    }

    private static String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_PREFIX);
        for (int i = 0; i < CODE_BODY_LEN; i++) {
            sb.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
        }
        return sb.toString();
    }
}
