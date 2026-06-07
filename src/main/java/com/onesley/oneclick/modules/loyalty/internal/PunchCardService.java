package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.exception.UnprocessableException;
import com.onesley.oneclick.modules.loyalty.api.PunchCardAdminDto;
import com.onesley.oneclick.modules.loyalty.api.PunchCardDto;
import com.onesley.oneclick.security.SecurityHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Logique des cartes de fidélité « punch cards » 10/1 (PCC Lot 3).
 *
 * <ul>
 *   <li>{@link #punch(UUID, UUID, String)} — upsert + incrément (+1) déclenché par
 *       l'event {@code ResourceBookingStatusChangedEvent → completed} (auto-punch via
 *       {@link ResourceBookingPunchListener}). Idempotent-safe : crée la carte si absente.</li>
 *   <li>{@link #listMyCards()} — cartes du user courant (self-scope).</li>
 *   <li>{@link #redeem(UUID)} — le staff applique une séance gratuite quand au moins un
 *       palier complet est disponible. ABAC staff/admin only ; 422 si aucun palier dispo.</li>
 *   <li>{@link #resolveActivity(String)} — mapping {@code resource_type → activité}.</li>
 * </ul>
 *
 * <p>Self-scope tenant : {@code listMyCards} filtre par {@code (tenantId, clientId)}. Le
 * tenant courant est résolu via {@link UserDirectoryApi#tenantIdById(UUID)} (contrat
 * core.identity, pas de SQL natif cross-module). Un tenant null → liste vide (le membre
 * OneClick standard n'a pas de cartes PCC).</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class PunchCardService {

    private final PunchCardRepository repo;
    /** Résolution du tenant du user courant (core.identity OPEN) — pas de JOIN users natif. */
    private final UserDirectoryApi userDirectory;

    /**
     * Mapping {@code resource_type → activité de carte}. Un type non mappé → pas de carte
     * (le listener ne punch pas). Extensible : ajouter une entrée ici suffit.
     *
     * <p>Aligné sur les ressources bookables PCC : padel, tennis, spa, golf, coiffeur, palm_gym.</p>
     */
    private static final Map<String, String> RESOURCE_TYPE_TO_ACTIVITY = Map.of(
        "padel_court",   "padel",
        "tennis_court",  "tennis",
        "spa_room",      "spa",
        "golf_tee",      "golf",
        "barber_chair",  "coiffeur",
        "coach_session", "palm_gym"
    );

    /**
     * Résout l'activité de carte à partir d'un {@code resource_type}.
     *
     * @return l'activité mappée, ou {@code Optional.empty()} si le type n'ouvre pas de carte.
     */
    public static Optional<String> resolveActivity(String resourceType) {
        if (resourceType == null) return Optional.empty();
        return Optional.ofNullable(RESOURCE_TYPE_TO_ACTIVITY.get(resourceType));
    }

    // ─── Punch (auto, via event) ─────────────────────────────────────────────

    /**
     * Incrémente (+1) la carte (tenant, client, activité) — upsert : crée la carte si
     * absente. Met à jour {@code lastPunchedAt}. Appelé par {@link ResourceBookingPunchListener}
     * quand un booking passe à {@code completed}.
     *
     * @return la carte mise à jour, mappée en DTO.
     */
    @Transactional
    public PunchCardDto punch(UUID tenantId, UUID clientId, String activity) {
        if (tenantId == null || clientId == null || activity == null) {
            throw new IllegalArgumentException("tenantId, clientId et activity sont requis pour punch");
        }
        PunchCard card = repo.findByTenantIdAndClientIdAndActivity(tenantId, clientId, activity)
            .orElseGet(() -> new PunchCard(UUID.randomUUID(), tenantId, clientId, activity));
        card.setCountPunched(card.getCountPunched() + 1);
        card.setLastPunchedAt(Instant.now());
        PunchCard saved = repo.save(card);
        log.info("[punch-card] +1 (tenant={}, client={}, activity={}) → count={}",
            tenantId, clientId, activity, saved.getCountPunched());
        return saved.toDto();
    }

    // ─── Lecture self-scope ────────────────────────────────────────────────────

    /**
     * Cartes du user courant dans le tenant courant (self-scope). Aucun paramètre :
     * l'identité vient du JWT. Un user sans tenant (OneClick standard) → liste vide.
     */
    public List<PunchCardDto> listMyCards() {
        UUID clientId = SecurityHelper.currentUserId();
        if (clientId == null) {
            throw new ForbiddenException("Authentification requise");
        }
        UUID tenantId = userDirectory.tenantIdById(clientId).orElse(null);
        if (tenantId == null) {
            return List.of(); // pas de tenant → pas de cartes PCC
        }
        return repo.findByTenantIdAndClientIdOrderByActivityAsc(tenantId, clientId)
            .stream().map(PunchCard::toDto).toList();
    }

    /**
     * Toutes les cartes d'un tenant avec identité du membre (export STAFF/admin — Gap #3,
     * port {@code export_punch_cards_csv}). Noms/téléphones résolus en batch via
     * {@link UserDirectoryApi#namesByIds(List)} (core.identity, pas de JOIN users natif).
     * Gating {@code VIEW:STAFF} porté par le controller.
     */
    public List<PunchCardAdminDto> listByTenant(UUID tenantId) {
        List<PunchCard> cards = repo.findByTenantIdOrderByActivityAscClientIdAsc(tenantId);
        if (cards.isEmpty()) return List.of();
        List<UUID> clientIds = cards.stream().map(PunchCard::getClientId).distinct().toList();
        Map<UUID, UserDirectoryApi.UserName> names = userDirectory.namesByIds(clientIds).stream()
            .collect(java.util.stream.Collectors.toMap(UserDirectoryApi.UserName::id, n -> n));
        return cards.stream().map(c -> {
            UserDirectoryApi.UserName n = names.get(c.getClientId());
            String name = n == null ? ""
                : (safe(n.firstName()) + " " + safe(n.lastName())).trim();
            String phone = n == null ? "" : safe(n.phone());
            return new PunchCardAdminDto(c.getClientId(), name, phone, c.getActivity(),
                c.getCountPunched(), c.getThreshold(), c.getRedeemedCount(), c.getLastPunchedAt());
        }).toList();
    }

    private static String safe(String s) { return s == null ? "" : s; }

    // ─── Redeem (staff applique une séance gratuite) ────────────────────────────

    /**
     * Le staff applique une séance gratuite sur une carte : {@code redeemedCount++} +
     * {@code lastRedeemedAt = now}, à condition qu'au moins un palier complet soit
     * disponible ({@code countPunched - redeemedCount*threshold >= threshold}).
     *
     * <p>ABAC : réservé staff/admin (le service le vérifie — un membre ne redeem pas
     * lui-même sa carte). {@code 403} sinon, {@code 422} si aucun palier dispo.</p>
     *
     * @throws ForbiddenException     (403) si l'appelant n'est pas staff/admin
     * @throws NotFoundException      (404) si la carte n'existe pas
     * @throws UnprocessableException (422) si aucun palier complet n'est disponible
     */
    @Transactional
    public PunchCardDto redeem(UUID cardId) {
        // ABAC : seul le staff/admin applique une séance gratuite.
        if (!SecurityHelper.isStaffOrAdmin()) {
            throw new ForbiddenException(
                "Accès interdit : seul le staff peut appliquer une séance gratuite (redeem)");
        }
        PunchCard card = repo.findById(cardId)
            .orElseThrow(() -> new NotFoundException("PunchCard", cardId));
        if (!card.hasRedeemablePalier()) {
            throw new UnprocessableException(
                "Aucune séance gratuite disponible : la carte n'a pas atteint un palier complet ("
                    + card.getCountPunched() + "/" + card.getThreshold()
                    + ", " + card.getRedeemedCount() + " déjà utilisée(s))");
        }
        card.setRedeemedCount(card.getRedeemedCount() + 1);
        card.setLastRedeemedAt(Instant.now());
        PunchCard saved = repo.save(card);
        log.info("[punch-card] redeem (card={}, activity={}) → redeemed={}, count={}",
            cardId, saved.getActivity(), saved.getRedeemedCount(), saved.getCountPunched());
        return saved.toDto();
    }
}
