package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyExtensionDtos.*;
import com.onesley.oneclick.security.SecurityHelper;
import static com.onesley.oneclick.shared.Temporals.toInstant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Service d'extension Sprint H pour les vues admin loyalty :
 * client ratings/scores, AI usage rate-limit, restitutions, expired points,
 * point distributions, restaurant tier status.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class LoyaltyExtensionService {

    private static final int AI_DAILY_LIMIT = 20;

    private final ClientRatingRepository ratingRepo;
    private final AIUsageRepository aiUsageRepo;
    private final RestaurantRestitutionRepository restitutionRepo;
    private final RestaurantTierStatusRepository tierStatusRepo;
    private final ClientScoreConfigRepository scoreConfigRepo;

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
        return new ClientScoreDto(userId, averageRating, count == null ? 0L : count, score,
            label, averageRating, total, honorees, noShows);
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

    public RestaurantRestitutionDto createRestitution(UUID restaurantId, BigDecimal amount, Integer points, String reason) {
        RestaurantRestitution r = new RestaurantRestitution();
        r.setRestaurantId(restaurantId);
        r.setAmount(amount);
        r.setPoints(points);
        r.setReason(reason);
        return RestaurantRestitutionDto.from(restitutionRepo.save(r));
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
        // LoyaltyTransaction n'a pas user_id/restaurant_id directs — JOIN via loyalty_accounts
        String sql = """
            SELECT la.client_id, COALESCE(u.first_name || ' ' || u.last_name, 'Unknown'),
                   ABS(lt.points) AS pts, lt.created_at,
                   la.restaurant_id, r.name
              FROM loyalty_transactions lt
              JOIN loyalty_accounts la ON la.id = lt.account_id
              LEFT JOIN users u ON u.id = la.client_id
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
        return rows.stream().map(row -> new ExpiredPointsAdminDto(
            row[0] != null ? (UUID) row[0] : null,
            (String) row[1],
            row[2] != null ? ((Number) row[2]).intValue() : 0,
            row[3] != null ? toInstant(row[3]) : null,
            row[4] != null ? (UUID) row[4] : null,
            (String) row[5]
        )).toList();
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
}
