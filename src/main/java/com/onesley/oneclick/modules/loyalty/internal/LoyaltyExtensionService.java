package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyExtensionDtos.*;
import com.onesley.oneclick.modules.loyalty.api.RedemptionDto;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.PageResponse;
import com.onesley.oneclick.shared.events.RestaurantRestitutionPaidEvent;
import static com.onesley.oneclick.shared.Temporals.toInstant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/**
 * Service d'extension Sprint H pour les vues admin loyalty :
 * client ratings/scores, AI usage rate-limit, restitutions, expired points,
 * point distributions, restaurant tier status.
 */
@Service
@Transactional
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class LoyaltyExtensionService {

    private static final int AI_DAILY_LIMIT = 20;

    private final ClientRatingRepository ratingRepo;
    private final LoyaltyEventRefGuard refGuard; // garde-fou FK client_ratings (reservation/user)
    private final AIUsageRepository aiUsageRepo;
    private final RestaurantRestitutionRepository restitutionRepo;
    private final RedemptionRepository redemptionRepo;
    private final RestaurantTierStatusRepository tierStatusRepo;
    private final ClientScoreConfigRepository scoreConfigRepo;
    /** P2 — noms clients via contrat identity (plus de JOIN users pour l'enrichissement). */
    private final UserDirectoryApi userDirectory;
    /** B6 — signal Modulith vers core.notification au versement d'une restitution (frontière OPEN shared.events). */
    private final ApplicationEventPublisher events;

    @PersistenceContext
    private EntityManager em;

    // ─── Client ratings ──────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<ClientRatingDto> findUserRatings(UUID userId) {
        return ratingRepo.findByUser(userId).stream().map(ClientRatingDto::from).toList();
    }

    @Transactional
    public ClientScoreDto computeUserScore(UUID userId) {
        Double avg = ratingRepo.averageVisibleRating(userId);
        Long count = ratingRepo.countByUser(userId);
        BigDecimal averageRating = avg == null ? new BigDecimal("5.0") : new BigDecimal(avg).setScale(2, RoundingMode.HALF_UP);
        BigDecimal score = averageRating.multiply(new BigDecimal("20")); // 0-100 scale

        // D4 — enrichissement : label de fiabilité (seuils config) + compteurs
        // réservations sur la fenêtre glissante. Lecture cross-domaine en SQL
        // natif (Modulith CLOSED : pas d'import du module reservation).
        ClientScoreConfig cfg = loadOrCreateScoreConfig();
        long total = 0L, honorees = 0L, noShows = 0L;
        try {
            Object[] row = (Object[]) em.createNativeQuery("""
                SELECT COUNT(*),
                       COUNT(*) FILTER (WHERE status = 'honored'),
                       COUNT(*) FILTER (WHERE status = 'no_show')
                FROM reservations
                WHERE client_id = :cid AND deleted_at IS NULL
                  AND reservation_at >= now() - make_interval(months => :months)
                """)
                .setParameter("cid", userId)
                .setParameter("months", cfg.getFenetreMois())
                .getSingleResult();
            if (row != null) {
                total = ((Number) row[0]).longValue();
                honorees = ((Number) row[1]).longValue();
                noShows = ((Number) row[2]).longValue();
            }
        } catch (RuntimeException ignored) {
            // Compteurs best-effort : ne jamais casser le score si la lecture échoue.
        }

        String label = scoreLabel(score, total, cfg);
        // `stars` = note affichable 0-5. null si AUCUNE réservation notée (aucune ligne
        // client_ratings) → les badges (web ClientScoreBadge + natifs ClientScoreFormat) rendent
        // « 🆕 Nouveau client » au lieu d'un ⭐5.0 trompeur (cas `totalReservations==0 && stars==null`).
        // `averageRating`/`score` gardent leur défaut (5.0 / 100) pour le calcul interne.
        BigDecimal stars = (count == null || count == 0L) ? null : averageRating;
        return new ClientScoreDto(userId, averageRating, count == null ? 0L : count, score,
            label, stars, total, honorees, noShows);
    }

    /** Label de fiabilité depuis le score (/100) et les seuils config. */
    private static String scoreLabel(BigDecimal scorePct, long total, ClientScoreConfig cfg) {
        if (total < cfg.getMinReservations()) return "Nouveau";
        double s = scorePct.doubleValue();
        if (s >= cfg.getSeuilExcellent().doubleValue()) return "Excellent";
        if (s >= cfg.getSeuilFiable().doubleValue()) return "Fiable";
        if (s >= cfg.getSeuilMoyen().doubleValue()) return "Moyen";
        return "Peu fiable";
    }

    // ─── Configuration du moteur de notation (singleton V52) ─────────
    /**
     * Config singleton de notation client. Seedée par V52 ; on recrée une ligne
     * par défaut de façon défensive si elle a été supprimée (jamais null).
     */
    @Transactional
    public ClientScoreConfigDto getScoreConfig() {
        return loadOrCreateScoreConfig().toDto();
    }

    @Transactional
    public ClientScoreConfigDto updateScoreConfig(ClientScoreConfigPatchDto dto) {
        ClientScoreConfig c = loadOrCreateScoreConfig();
        if (dto.minReservations() != null)       c.setMinReservations(dto.minReservations());
        if (dto.seuilExcellent() != null)        c.setSeuilExcellent(dto.seuilExcellent());
        if (dto.seuilFiable() != null)           c.setSeuilFiable(dto.seuilFiable());
        if (dto.seuilMoyen() != null)            c.setSeuilMoyen(dto.seuilMoyen());
        if (dto.scoreInitial() != null)          c.setScoreInitial(dto.scoreInitial());
        if (dto.penaliteNoShow() != null)        c.setPenaliteNoShow(dto.penaliteNoShow());
        if (dto.honoreesPourRemonter() != null)  c.setHonoreesPourRemonter(dto.honoreesPourRemonter());
        if (dto.gainParPalier() != null)         c.setGainParPalier(dto.gainParPalier());
        if (dto.fenetreMois() != null)           c.setFenetreMois(dto.fenetreMois());
        return scoreConfigRepo.save(c).toDto();
    }

    private ClientScoreConfig loadOrCreateScoreConfig() {
        return scoreConfigRepo.findFirstByOrderByCreatedAtAsc()
            .orElseGet(() -> scoreConfigRepo.save(new ClientScoreConfig()));
    }

    public ClientRatingDto recordRating(UUID userId, UUID reservationId, BigDecimal delta, String reason) {
        // Garde-fou FK : si la réservation ou le user référencé n'existe plus (ex: event Modulith
        // dormant rejoué après suppression), on saute l'insert au lieu de violer la FK au COMMIT
        // (ce qui ferait échouer le listener async + laisserait la publication incomplète → rejeu
        // en boucle). Retour null = rating non enregistré (cf appelants : listener ignore, controller → 404).
        if (!refGuard.refsExist(userId, reservationId)) {
            log.warn("[loyalty-rating] user {} ou résa {} absent — rating ignoré (reason={})",
                userId, reservationId, reason);
            return null;
        }
        var existing = ratingRepo.findByUser(userId);
        BigDecimal current = existing.isEmpty() ? new BigDecimal("5.0") : existing.get(0).getVisibleRating();
        BigDecimal next = current.add(delta).max(BigDecimal.ZERO).min(new BigDecimal("5.0"))
            .setScale(1, RoundingMode.HALF_UP);
        ClientRating r = new ClientRating();
        r.setUserId(userId);
        r.setReservationId(reservationId);
        r.setRating(next);
        r.setVisibleRating(next);
        r.setDelta(delta);
        r.setReason(reason);
        return ClientRatingDto.from(ratingRepo.save(r));
    }

    // ─── AI usage rate limit ─────────────────────────────────────────
    @Transactional(readOnly = true)
    public AIUsageDto findUsage(UUID userId) {
        var u = aiUsageRepo.findByUserId(userId).orElse(null);
        if (u != null && u.getLastPromptAt() != null) {
            Instant resetTime = u.getLastPromptAt().plusSeconds(86_400);
            if (Instant.now().isAfter(resetTime)) {
                u.setPromptCount(0);
                aiUsageRepo.save(u);
            }
        }
        return AIUsageDto.from(u, AI_DAILY_LIMIT);
    }

    public AIUsageDto incrementUsage(UUID userId) {
        var u = aiUsageRepo.findByUserId(userId).orElseGet(() -> {
            AIUsage created = new AIUsage();
            created.setUserId(userId);
            return created;
        });
        // Reset window after 24h
        if (u.getLastPromptAt() != null && Instant.now().isAfter(u.getLastPromptAt().plusSeconds(86_400))) {
            u.setPromptCount(0);
        }
        u.setPromptCount((u.getPromptCount() == null ? 0 : u.getPromptCount()) + 1);
        u.setLastPromptAt(Instant.now());
        return AIUsageDto.from(aiUsageRepo.save(u), AI_DAILY_LIMIT);
    }

    // ─── Restitutions ────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<RestaurantRestitutionDto> findRestaurantRestitutions(UUID restaurantId) {
        return restitutionRepo.findByRestaurant(restaurantId).stream()
            .map(RestaurantRestitutionDto::from).toList();
    }

    /**
     * Batch (B1.5) — restitutions de plusieurs restaurants en UNE requête (remplace
     * le fan-out N+1 de useRestitutions). L'ABAC est appliquée par le contrôleur
     * (admin → tout ; sinon staff actif de chaque resto demandé).
     */
    @Transactional(readOnly = true)
    public List<RestaurantRestitutionDto> findRestitutionsByRestaurants(List<UUID> restaurantIds) {
        if (restaurantIds == null || restaurantIds.isEmpty()) return List.of();
        return restitutionRepo.findByRestaurants(restaurantIds).stream()
            .map(RestaurantRestitutionDto::from).toList();
    }

    public RestaurantRestitutionDto createRestitution(UUID restaurantId, BigDecimal amount, Integer points, String reason) {
        RestaurantRestitution r = new RestaurantRestitution();
        r.setRestaurantId(restaurantId);
        r.setAmount(amount);
        r.setPoints(points);
        r.setReason(reason);
        return RestaurantRestitutionDto.from(restitutionRepo.save(r));
    }

    /**
     * B6 — VERSE une restitution (passe son statut à {@code paid}) et notifie le staff du restaurant
     * bénéficiaire. Idempotent : une restitution déjà {@code paid} n'est ni re-marquée ni re-notifiée
     * (pas de double notif). 404 si la restitution n'existe pas.
     *
     * <p>Frontière Modulith : on résout ici (côté loyalty) les destinataires staff actifs du resto
     * (requête native sur {@code restaurant_staffs} — table de {@code modules.restaurant}, non
     * importable, on reste au niveau SQL comme {@code NoShowDisputeService}) et on les porte sur
     * {@link RestaurantRestitutionPaidEvent} ; {@code core.notification} (CLOSED) n'a qu'à itérer.
     * Le push éventuel et le filtre de préférences staff (catégorie {@code loyalty}) sont appliqués
     * côté listener.
     *
     * @return la restitution (statut {@code paid})
     */
    public RestaurantRestitutionDto payRestitution(UUID restitutionId) {
        RestaurantRestitution r = restitutionRepo.findById(restitutionId)
            .orElseThrow(() -> new NotFoundException("RestaurantRestitution", restitutionId));
        if ("paid".equals(r.getStatus())) {
            // déjà versée → idempotent (ni re-save ni re-notif), retour de l'état courant
            return RestaurantRestitutionDto.from(r);
        }
        r.setStatus("paid");
        RestaurantRestitution saved = restitutionRepo.save(r);

        List<UUID> staffRecipientIds = staffRecipientIdsForRestaurant(saved.getRestaurantId());
        events.publishEvent(new RestaurantRestitutionPaidEvent(
            saved.getId(), saved.getRestaurantId(), saved.getAmount(),
            staffRecipientIds, Instant.now()));
        log.info("[restitution] versée (id={}, resto={}, montant={}, staff notifiés={})",
            saved.getId(), saved.getRestaurantId(), saved.getAmount(), staffRecipientIds.size());

        return RestaurantRestitutionDto.from(saved);
    }

    /**
     * B6 — IDs des staff ACTIFS du restaurant (destinataires de la notif « restitution versée »).
     * Staff actif = {@code restaurant_staffs.deleted_at IS NULL} (la table n'a pas de colonne status).
     * SQL natif (noms de tables) : la résolution reste côté module loyalty et les UUID sont portés
     * sur {@code RestaurantRestitutionPaidEvent} (frontière Modulith). {@code DISTINCT} dédoublonne.
     * Calque {@code NoShowDisputeService.staffRecipientIdsForRestaurant}.
     */
    @SuppressWarnings("unchecked")
    private List<UUID> staffRecipientIdsForRestaurant(UUID restaurantId) {
        return em.createNativeQuery("""
                SELECT DISTINCT rs.user_id FROM restaurant_staffs rs
                 WHERE rs.restaurant_id = :restaurantId
                   AND rs.deleted_at IS NULL
                """)
            .setParameter("restaurantId", restaurantId)
            .getResultList();
    }

    // ─── Audit listings (Forge — RedemptionAudit / RestaurantRestitutions) ──────

    /**
     * Audit paginé des rédemptions ({@code /forge/redemptions}).
     *
     * <p><b>ABAC</b> : admin (SUPERADMIN/GROUP_ADMIN) → toutes ; non-admin (RESTAURATEUR/STAFF)
     * → uniquement les rédemptions des restaurants dont il est staff actif. Un CLIENT (qui
     * détient pourtant VIEW:LOYALTY) n'est staff d'aucun resto → page vide. Si {@code restaurantId}
     * est fourni par un non-admin et n'appartient pas à son périmètre → {@code 403} (porté par
     * le contrôleur avant l'appel).
     *
     * <p><b>Anti-N+1</b> : la page est lue en 2 requêtes (rows + count) via JOIN
     * {@code loyalty_accounts} (dérive client/resto) + read-view {@code restaurants} (nom resto),
     * puis <b>un seul</b> batch {@code UserDirectoryApi.namesByIds} résout tous les noms clients
     * de la page. Quel que soit {@code size}, 3 requêtes au total.
     *
     * @param status filtre optionnel sur la validation OTP : {@code "otp_validated"} (gros
     *               montants) / {@code "standard"}. La table {@code redemptions} ne porte pas de
     *               statut accepté/refusé (toute rédemption est effective) ; {@code null}/valeur
     *               inconnue = aucun filtre.
     */
    @Transactional(readOnly = true)
    public PageResponse<RedemptionDto> findRedemptionsAudit(
            UUID restaurantId, Instant from, Instant to, String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        // Scope ABAC : admin = pas de restriction ; sinon restreint aux restos du staff.
        List<UUID> scopeRestaurantIds = adminOrNull();
        if (scopeRestaurantIds != null && scopeRestaurantIds.isEmpty()) {
            return PageResponse.from(new PageImpl<RedemptionDto>(List.of(), pageable, 0));
        }

        // Filtre OTP optionnel (seul "statut" disponible sur l'entité Redemption).
        Boolean otpFilter = otpFilter(status);

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        if (restaurantId != null)        where.append(" AND la.restaurant_id = :restaurantId ");
        if (scopeRestaurantIds != null)  where.append(" AND la.restaurant_id IN :scope ");
        if (from != null)                where.append(" AND rd.created_at >= :from ");
        if (to != null)                  where.append(" AND rd.created_at <  :to ");
        if (otpFilter != null)           where.append(" AND rd.otp_validated = :otp ");

        Number total = (Number) bind(em.createNativeQuery(
                "SELECT COUNT(*) FROM redemptions rd "
                + "JOIN loyalty_accounts la ON la.id = rd.account_id" + where),
                restaurantId, scopeRestaurantIds, from, to, otpFilter).getSingleResult();
        long totalElements = total.longValue();
        if (totalElements == 0) {
            return PageResponse.from(new PageImpl<RedemptionDto>(List.of(), pageable, 0));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = bind(em.createNativeQuery("""
                SELECT rd.id, rd.account_id, la.client_id, la.restaurant_id, r.name,
                       rd.points_used, rd.discount_amount, rd.otp_validated, rd.created_at
                  FROM redemptions rd
                  JOIN loyalty_accounts la ON la.id = rd.account_id
                  LEFT JOIN restaurants r ON r.id = la.restaurant_id
                """ + where + " ORDER BY rd.created_at DESC "),
                restaurantId, scopeRestaurantIds, from, to, otpFilter)
            .setFirstResult((int) pageable.getOffset())
            .setMaxResults(pageable.getPageSize())
            .getResultList();

        // Batch résolution des noms clients (anti-N+1, un seul appel pour la page).
        List<UUID> clientIds = rows.stream()
            .map(row -> (UUID) row[2]).filter(Objects::nonNull).distinct().toList();
        Map<UUID, String> nameByClient = userDirectory.namesByIds(clientIds).stream()
            .collect(Collectors.toMap(UserDirectoryApi.UserName::id,
                LoyaltyExtensionService::fullName, (a, b) -> a));

        List<RedemptionDto> content = rows.stream().map(row -> {
            UUID clientId = row[2] != null ? (UUID) row[2] : null;
            return new RedemptionDto(
                row[0] != null ? (UUID) row[0] : null,
                row[1] != null ? (UUID) row[1] : null,
                clientId,
                clientId != null ? nameByClient.get(clientId) : null,
                row[3] != null ? (UUID) row[3] : null,
                (String) row[4],
                row[5] != null ? ((Number) row[5]).intValue() : 0,
                row[6] != null ? (BigDecimal) row[6] : null,
                row[7] != null && (Boolean) row[7],
                row[8] != null ? toInstant(row[8]) : null
            );
        }).toList();

        return PageResponse.from(new PageImpl<>(content, pageable, totalElements));
    }

    /**
     * Audit paginé des restitutions resto ({@code /forge/restitutions}).
     *
     * <p>Même <b>ABAC</b> que {@link #findRedemptionsAudit} (admin → tout ; owner → ses restos ;
     * client → vide). Filtres optionnels {@code restaurantId} + fenêtre {@code [from, to[}.
     *
     * <p>Implémentation native à {@code WHERE} dynamique (clause ajoutée seulement si le filtre
     * est présent) — alignée sur {@link #findRedemptionsAudit} et {@code findExpiredPointsAdmin}.
     * Évite le piège PostgreSQL {@code could not determine data type of parameter} du motif JPQL
     * {@code :param IS NULL OR ...} sur un bind {@code null} non typé. Page lue en 2 requêtes
     * (count + rows) ; {@code restaurantName} enrichi via read-view {@code restaurants} en
     * <b>un seul</b> batch (anti-N+1).
     */
    @Transactional(readOnly = true)
    public PageResponse<RestaurantRestitutionDto> findRestitutionsAudit(
            UUID restaurantId, Instant from, Instant to, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        List<UUID> scopeRestaurantIds = adminOrNull();
        if (scopeRestaurantIds != null && scopeRestaurantIds.isEmpty()) {
            return PageResponse.from(new PageImpl<RestaurantRestitutionDto>(List.of(), pageable, 0));
        }

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        if (restaurantId != null)        where.append(" AND rr.restaurant_id = :restaurantId ");
        if (scopeRestaurantIds != null)  where.append(" AND rr.restaurant_id IN :scope ");
        if (from != null)                where.append(" AND rr.created_at >= :from ");
        if (to != null)                  where.append(" AND rr.created_at <  :to ");

        Number total = (Number) bind(em.createNativeQuery(
                "SELECT COUNT(*) FROM restaurant_restitutions rr" + where),
                restaurantId, scopeRestaurantIds, from, to, null).getSingleResult();
        long totalElements = total.longValue();
        if (totalElements == 0) {
            return PageResponse.from(new PageImpl<RestaurantRestitutionDto>(List.of(), pageable, 0));
        }

        @SuppressWarnings("unchecked")
        List<RestaurantRestitution> rows = bind(em.createNativeQuery(
                "SELECT rr.* FROM restaurant_restitutions rr" + where
                + " ORDER BY rr.created_at DESC", RestaurantRestitution.class),
                restaurantId, scopeRestaurantIds, from, to, null)
            .setFirstResult((int) pageable.getOffset())
            .setMaxResults(pageable.getPageSize())
            .getResultList();

        // Enrichissement noms resto via read-view native (batch unique pour la page).
        List<UUID> restoIds = rows.stream()
            .map(RestaurantRestitution::getRestaurantId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, String> nameByResto = restaurantNames(restoIds);

        List<RestaurantRestitutionDto> content = rows.stream()
            .map(r -> RestaurantRestitutionDto.from(r, nameByResto.get(r.getRestaurantId())))
            .toList();

        return PageResponse.from(new PageImpl<>(content, pageable, totalElements));
    }

    // ─── Helpers (audit) ────────────────────────────────────────────────────────

    /**
     * Scope ABAC commun aux audits : {@code null} si l'appelant est admin (= pas de
     * restriction par restaurant), sinon la liste (possiblement vide) des restaurants dont
     * il est staff actif (read-view {@code restaurant_staffs}). {@code 401} si non authentifié.
     */
    @SuppressWarnings("unchecked")
    private List<UUID> adminOrNull() {
        if (SecurityHelper.isAdmin()) return null;
        UUID callerId = SecurityHelper.currentUserId();
        if (callerId == null) throw new ForbiddenException("Authentification requise");
        return em.createNativeQuery("""
                SELECT restaurant_id FROM restaurant_staffs
                 WHERE user_id = :uid AND deleted_at IS NULL
                """)
            .setParameter("uid", callerId)
            .getResultList();
    }

    /** Noms des restaurants par id (read-view native — Modulith CLOSED). Batch anti-N+1. */
    @SuppressWarnings("unchecked")
    private Map<UUID, String> restaurantNames(List<UUID> restaurantIds) {
        if (restaurantIds.isEmpty()) return Map.of();
        List<Object[]> rows = em.createNativeQuery(
                "SELECT id, name FROM restaurants WHERE id IN :ids")
            .setParameter("ids", restaurantIds)
            .getResultList();
        return rows.stream().collect(Collectors.toMap(
            row -> (UUID) row[0], row -> (String) row[1], (a, b) -> a));
    }

    /** Lie les paramètres optionnels présents dans le {@code WHERE} dynamique. */
    private static jakarta.persistence.Query bind(
            jakarta.persistence.Query q, UUID restaurantId, List<UUID> scope,
            Instant from, Instant to, Boolean otp) {
        if (restaurantId != null) q.setParameter("restaurantId", restaurantId);
        if (scope != null)        q.setParameter("scope", scope);
        if (from != null)         q.setParameter("from", java.sql.Timestamp.from(from));
        if (to != null)           q.setParameter("to", java.sql.Timestamp.from(to));
        if (otp != null)          q.setParameter("otp", otp);
        return q;
    }

    /** {@code "otp_validated"}→true, {@code "standard"}→false, sinon {@code null} (pas de filtre). */
    private static Boolean otpFilter(String status) {
        if (status == null) return null;
        String s = status.trim().toLowerCase();
        if (s.equals("otp_validated") || s.equals("otp")) return Boolean.TRUE;
        if (s.equals("standard") || s.equals("no_otp"))   return Boolean.FALSE;
        return null;
    }

    /** "Prénom Nom" depuis la projection annuaire (null si toutes composantes vides). */
    private static String fullName(UserDirectoryApi.UserName u) {
        if (u == null) return null;
        String first = u.firstName() == null ? "" : u.firstName().trim();
        String last = u.lastName() == null ? "" : u.lastName().trim();
        String full = (first + " " + last).trim();
        return full.isEmpty() ? null : full;
    }

    // ─── Restaurant tier status ──────────────────────────────────────
    @Transactional(readOnly = true)
    public RestaurantTierStatusDto getRestaurantTier(UUID restaurantId) {
        var status = tierStatusRepo.findByRestaurantId(restaurantId)
            .orElseGet(() -> {
                RestaurantTierStatus s = new RestaurantTierStatus();
                s.setRestaurantId(restaurantId);
                return s;
            });
        return RestaurantTierStatusDto.from(status);
    }

    // ─── Expired points admin views (via native SQL) ─────────────────

    /**
     * RBAC : admin (SUPERADMIN/GROUP_ADMIN) → tout ; RESTAURATEUR/STAFF → uniquement
     * leur propre restaurant (restaurantId obligatoire + check staff actif).
     * Sans cette logique, le frontend Facturation (OneClickHIPro) 403 systématique.
     */
    private void requireAdminOrStaffOf(UUID restaurantId) {
        if (SecurityHelper.isAdmin()) return;
        UUID callerId = SecurityHelper.currentUserId();
        if (callerId == null) throw new ForbiddenException("Non authentifié");
        if (restaurantId == null) {
            throw new BadRequestException(
                "restaurantId obligatoire pour staff non-admin (filtrage scope tenant)"
            );
        }
        Number count = (Number) em.createNativeQuery("""
            SELECT COUNT(*) FROM restaurant_staffs
             WHERE user_id = :userId AND restaurant_id = :restaurantId
               AND deleted_at IS NULL
            """)
            .setParameter("userId", callerId)
            .setParameter("restaurantId", restaurantId)
            .getSingleResult();
        if (count.longValue() == 0) {
            throw new ForbiddenException("Accès refusé : vous n'êtes pas staff de ce restaurant");
        }
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<ExpiredPointsAdminDto> findExpiredPointsAdmin(UUID restaurantId, int limit) {
        requireAdminOrStaffOf(restaurantId);
        // LoyaltyTransaction n'a pas user_id/restaurant_id directs — JOIN via loyalty_accounts.
        // P2 (hybride core-only) : on garde le JOIN restaurants (pair business → read-view native
        // délibérée) ; le nom client vient de UserDirectoryApi (core.identity), plus de JOIN users.
        String sql = """
            SELECT la.client_id, ABS(lt.points) AS pts, lt.created_at,
                   la.restaurant_id, r.name
              FROM loyalty_transactions lt
              JOIN loyalty_accounts la ON la.id = lt.account_id
              LEFT JOIN restaurants r ON r.id = la.restaurant_id
             WHERE lt.type = 'expire'
             """ + (restaurantId != null ? " AND la.restaurant_id = :restaurantId " : "") + """
             ORDER BY lt.created_at DESC
             LIMIT :limit
            """;
        var q = em.createNativeQuery(sql);
        if (restaurantId != null) q.setParameter("restaurantId", restaurantId);
        q.setParameter("limit", limit);
        List<Object[]> rows = q.getResultList();

        // Enrichissement noms via contrat identity (anti-N+1, un seul batch).
        List<UUID> clientIds = rows.stream()
            .map(row -> (UUID) row[0]).filter(java.util.Objects::nonNull).distinct().toList();
        java.util.Map<UUID, UserDirectoryApi.UserName> names = userDirectory.namesByIds(clientIds).stream()
            .collect(java.util.stream.Collectors.toMap(UserDirectoryApi.UserName::id, n -> n, (a, b) -> a));

        return rows.stream().map(row -> {
            UUID clientId = row[0] != null ? (UUID) row[0] : null;
            UserDirectoryApi.UserName n = clientId != null ? names.get(clientId) : null;
            // Parité avec l'ancien COALESCE(first || ' ' || last, 'Unknown') : null si un nom manque.
            String fullName = (n != null && n.firstName() != null && n.lastName() != null)
                ? (n.firstName() + " " + n.lastName()) : "Unknown";
            return new ExpiredPointsAdminDto(
                clientId,
                fullName,
                row[1] != null ? ((Number) row[1]).intValue() : 0,
                row[2] != null ? toInstant(row[2]) : null,
                row[3] != null ? (UUID) row[3] : null,
                (String) row[4]
            );
        }).toList();
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<PointDistributionDto> findPointDistributions(UUID restaurantId, UUID userId, int limit) {
        // LoyaltyTransaction n'a pas user_id/restaurant_id directs — JOIN via loyalty_accounts
        String filter = "";
        if (restaurantId != null) filter += " AND la.restaurant_id = :restaurantId ";
        if (userId != null)        filter += " AND la.client_id = :userId ";
        String sql = """
            SELECT lt.id, la.client_id, la.restaurant_id, lt.points, lt.reason, lt.created_at,
                   la.balance, lt.created_by
              FROM loyalty_transactions lt
              JOIN loyalty_accounts la ON la.id = lt.account_id
             WHERE lt.points > 0
             """ + filter + """
             ORDER BY lt.created_at DESC
             LIMIT :limit
            """;
        var q = em.createNativeQuery(sql);
        if (restaurantId != null) q.setParameter("restaurantId", restaurantId);
        if (userId != null)        q.setParameter("userId", userId);
        q.setParameter("limit", limit);
        List<Object[]> rows = q.getResultList();
        return rows.stream().map(row -> new PointDistributionDto(
            row[0] != null ? (UUID) row[0] : null,
            row[1] != null ? (UUID) row[1] : null,
            row[2] != null ? (UUID) row[2] : null,
            row[3] != null ? ((Number) row[3]).intValue() : 0,
            (String) row[4],
            row[5] != null ? toInstant(row[5]) : null,
            row[6] != null ? ((Number) row[6]).intValue() : null,
            row[7] != null ? (UUID) row[7] : null
        )).toList();
    }

    /**
     * Agrégat plateforme de l'économie de points (page admin OneClick Lounge).
     * Cross-restaurant → réservé admin (VIEW:ANALYTICS côté contrôleur).
     *
     * <p>Calcule serveur-side sur {@code loyalty_transactions} : émis (points&gt;0),
     * consommés ({@code spend}), expirés ({@code expire}), + répartition par
     * catégorie (déduite du {@code reason}) et tendance des 6 derniers mois. Évite
     * de rapatrier 10 000 lignes côté client et corrige le décalage de l'ancienne
     * vue {@code /point-distributions} (qui n'exposait que les émissions).
     */
    @Transactional(readOnly = true)
    public PointsEconomyDto getPointsEconomy() {
        Object[] t = (Object[]) em.createNativeQuery("""
            SELECT COALESCE(SUM(CASE WHEN points > 0 THEN points ELSE 0 END), 0)       AS emitted,
                   COALESCE(SUM(CASE WHEN type = 'spend'  THEN -points ELSE 0 END), 0) AS consumed,
                   COALESCE(SUM(CASE WHEN type = 'expire' THEN -points ELSE 0 END), 0) AS expired,
                   COALESCE(SUM(CASE WHEN points > 0 THEN 1 ELSE 0 END), 0)            AS emitting_count
              FROM loyalty_transactions
            """).getSingleResult();
        long emitted = ((Number) t[0]).longValue();
        long consumed = ((Number) t[1]).longValue();
        long expired = ((Number) t[2]).longValue();
        long emittingCount = ((Number) t[3]).longValue();

        @SuppressWarnings("unchecked")
        List<Object[]> typeRows = em.createNativeQuery("""
            SELECT CASE
                     WHEN reason LIKE 'snap2earn|%' THEN 'snap2earn'
                     WHEN reason = 'welcome'        THEN 'welcome'
                     WHEN reason LIKE 'gift:%'      THEN 'gift'
                     ELSE 'autre'
                   END      AS type,
                   COUNT(*)  AS cnt
              FROM loyalty_transactions
             WHERE points > 0
             GROUP BY 1
             ORDER BY cnt DESC
            """).getResultList();
        List<PointsEconomyDto.TypeBucket> byType = typeRows.stream()
            .map(r -> new PointsEconomyDto.TypeBucket((String) r[0], ((Number) r[1]).longValue()))
            .toList();

        @SuppressWarnings("unchecked")
        List<Object[]> monthRows = em.createNativeQuery("""
            SELECT to_char(date_trunc('month', created_at), 'YYYY-MM')               AS ym,
                   COALESCE(SUM(CASE WHEN points > 0 THEN points ELSE 0 END), 0)       AS emitted,
                   COALESCE(SUM(CASE WHEN type = 'spend'  THEN -points ELSE 0 END), 0) AS consumed,
                   COALESCE(SUM(CASE WHEN type = 'expire' THEN -points ELSE 0 END), 0) AS expired
              FROM loyalty_transactions
             WHERE created_at >= date_trunc('month', NOW()) - INTERVAL '5 months'
             GROUP BY 1
             ORDER BY 1
            """).getResultList();
        List<PointsEconomyDto.MonthlyPoint> monthly = monthRows.stream()
            .map(r -> new PointsEconomyDto.MonthlyPoint(
                (String) r[0], ((Number) r[1]).longValue(),
                ((Number) r[2]).longValue(), ((Number) r[3]).longValue()))
            .toList();

        return new PointsEconomyDto(
            emitted, consumed, expired, emitted - consumed - expired, emittingCount, byType, monthly);
    }

    /**
     * Distribution des membres par palier de fidélité (vue admin /fidelite).
     *
     * <p>Points globaux d'un client = somme des soldes de ses comptes ; on le
     * bucketise sur le palier le plus élevé de SON tenant dont {@code min_points}
     * ≤ points. {@code LEFT JOIN tiers} → tous les paliers remontent (même 0 membre).
     * Cross-domaine en SQL natif (Modulith CLOSED). Réservé admin (VIEW:ANALYTICS).
     */
    @Transactional(readOnly = true)
    public List<TierDistributionDto> tierDistribution() {
        String sql = """
            WITH client_points AS (
                SELECT a.client_id, u.tenant_id AS tenant_id, COALESCE(SUM(a.balance), 0) AS pts
                  FROM loyalty_accounts a
                  JOIN users u ON u.id = a.client_id
                 WHERE u.deleted_at IS NULL
                 GROUP BY a.client_id, u.tenant_id
            ),
            ranked AS (
                SELECT cp.client_id,
                       (SELECT t.id FROM tiers t
                         WHERE t.tenant_id = cp.tenant_id AND t.min_points <= cp.pts
                         ORDER BY t.min_points DESC LIMIT 1) AS tier_id
                  FROM client_points cp
            )
            SELECT t.id, t.tenant_id, t.name, t.min_points, COUNT(r.client_id)
              FROM tiers t
              LEFT JOIN ranked r ON r.tier_id = t.id
             GROUP BY t.id, t.tenant_id, t.name, t.min_points
             ORDER BY t.tenant_id, t.min_points
            """;
        List<Object[]> rows = em.createNativeQuery(sql).getResultList();
        return rows.stream().map(row -> new TierDistributionDto(
            row[0] != null ? (UUID) row[0] : null,
            row[1] != null ? (UUID) row[1] : null,
            (String) row[2],
            row[3] != null ? ((Number) row[3]).intValue() : 0,
            row[4] != null ? ((Number) row[4]).longValue() : 0L
        )).toList();
    }

    /**
     * Agrégat crédit d'UN restaurant (fiche resto — onglets Clients/Staff).
     *
     * <p>Remplace le pull de 10 000 lignes que ClientSummary/StaffSummary faisaient via
     * {@code /point-distributions} : on calcule serveur-side en 2 requêtes natives
     * (totaux + répartition par membre). Cross-domaine en SQL natif (Modulith CLOSED).
     */
    @Transactional(readOnly = true)
    public RestaurantCreditSummaryDto restaurantCreditSummary(UUID restaurantId) {
        Object[] t = (Object[]) em.createNativeQuery("""
            SELECT
              COALESCE(SUM(CASE WHEN lt.points > 0 THEN lt.points ELSE 0 END), 0)      AS accorde,
              COALESCE(SUM(CASE WHEN lt.type = 'spend' THEN -lt.points ELSE 0 END), 0) AS consomme,
              (SELECT COALESCE(SUM(balance), 0) FROM loyalty_accounts WHERE restaurant_id = :rid) AS dispo
              FROM loyalty_transactions lt
              JOIN loyalty_accounts la ON la.id = lt.account_id
             WHERE la.restaurant_id = :rid
            """)
            .setParameter("rid", restaurantId)
            .getSingleResult();
        long accorde = ((Number) t[0]).longValue();
        long consomme = ((Number) t[1]).longValue();
        long dispo = ((Number) t[2]).longValue();

        @SuppressWarnings("unchecked")
        List<Object[]> memberRows = em.createNativeQuery("""
            SELECT lt.created_by, COALESCE(SUM(lt.points), 0) AS pts
              FROM loyalty_transactions lt
              JOIN loyalty_accounts la ON la.id = lt.account_id
             WHERE la.restaurant_id = :rid AND lt.points > 0 AND lt.created_by IS NOT NULL
             GROUP BY lt.created_by
             ORDER BY pts DESC
             LIMIT 10
            """)
            .setParameter("rid", restaurantId)
            .getResultList();
        List<RestaurantCreditSummaryDto.MemberCredit> byMember = memberRows.stream()
            .map(r -> new RestaurantCreditSummaryDto.MemberCredit(
                r[0] != null ? (UUID) r[0] : null,
                r[1] != null ? ((Number) r[1]).longValue() : 0L))
            .toList();

        return new RestaurantCreditSummaryDto(restaurantId, accorde, consomme, dispo, byMember);
    }
}
