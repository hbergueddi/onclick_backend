package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyExtensionDtos.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service d'extension Sprint H pour les vues admin loyalty :
 * client ratings/scores, AI usage rate-limit, restitutions, expired points,
 * point distributions, restaurant tier status.
 */
@Service
@Transactional
public class LoyaltyExtensionService {

    private static final int AI_DAILY_LIMIT = 20;

    private final ClientRatingRepository ratingRepo;
    private final AIUsageRepository aiUsageRepo;
    private final RestaurantRestitutionRepository restitutionRepo;
    private final RestaurantTierStatusRepository tierStatusRepo;

    @PersistenceContext
    private EntityManager em;

    public LoyaltyExtensionService(
        ClientRatingRepository ratingRepo,
        AIUsageRepository aiUsageRepo,
        RestaurantRestitutionRepository restitutionRepo,
        RestaurantTierStatusRepository tierStatusRepo
    ) {
        this.ratingRepo = ratingRepo;
        this.aiUsageRepo = aiUsageRepo;
        this.restitutionRepo = restitutionRepo;
        this.tierStatusRepo = tierStatusRepo;
    }

    // ─── Client ratings ──────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<ClientRatingDto> findUserRatings(UUID userId) {
        return ratingRepo.findByUser(userId).stream().map(ClientRatingDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ClientScoreDto computeUserScore(UUID userId) {
        Double avg = ratingRepo.averageVisibleRating(userId);
        Long count = ratingRepo.countByUser(userId);
        BigDecimal averageRating = avg == null ? new BigDecimal("5.0") : new BigDecimal(avg).setScale(2, RoundingMode.HALF_UP);
        BigDecimal score = averageRating.multiply(new BigDecimal("20")); // 0-100 scale
        return new ClientScoreDto(userId, averageRating, count == null ? 0L : count, score);
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
    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<ExpiredPointsAdminDto> findExpiredPointsAdmin(UUID restaurantId, int limit) {
        String sql = """
            SELECT lt.user_id, COALESCE(u.first_name || ' ' || u.last_name, 'Unknown'),
                   lt.amount * -1 AS pts, lt.created_at,
                   lt.restaurant_id, r.name
              FROM loyalty_transactions lt
              LEFT JOIN users u ON u.id = lt.user_id
              LEFT JOIN restaurants r ON r.id = lt.restaurant_id
             WHERE lt.reason = 'expired'
             """ + (restaurantId != null ? " AND lt.restaurant_id = :restaurantId " : "") + """
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
            row[3] != null ? ((java.sql.Timestamp) row[3]).toInstant() : null,
            row[4] != null ? (UUID) row[4] : null,
            (String) row[5]
        )).toList();
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<PointDistributionDto> findPointDistributions(UUID restaurantId, UUID userId, int limit) {
        String filter = "";
        if (restaurantId != null) filter += " AND restaurant_id = :restaurantId ";
        if (userId != null)        filter += " AND user_id = :userId ";
        String sql = """
            SELECT id, user_id, restaurant_id, amount, reason, created_at
              FROM loyalty_transactions
             WHERE amount > 0
             """ + filter + """
             ORDER BY created_at DESC
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
            row[5] != null ? ((java.sql.Timestamp) row[5]).toInstant() : null
        )).toList();
    }
}
