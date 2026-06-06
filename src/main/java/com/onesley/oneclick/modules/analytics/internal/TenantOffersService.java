package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.TenantOfferDtos.OfferRestaurantRefDto;
import com.onesley.oneclick.modules.analytics.api.TenantOfferDtos.TenantOfferDto;
import com.onesley.oneclick.modules.analytics.api.TenantOfferDtos.TenantOffersResultDto;
import com.onesley.oneclick.modules.analytics.api.TenantOfferDtos.TenantOffersSummaryDto;
import static com.onesley.oneclick.shared.Temporals.toInstant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Service portail tenant-admin « Mes promos » (C4.4).
 *
 * <p>Native SQL (Modulith CLOSED ; calque {@code TenantClientsService}). Les offres Spring n'ont
 * pas de {@code tenant_id} : scope via JOIN {@code restaurants} ({@code tenant_id}). Stats =
 * {@code offer_impressions} (G1). Résumé = fonction pure {@link #summarize} (testée unitairement).
 */
@Service
@Transactional(readOnly = true)
public class TenantOffersService {

    @PersistenceContext
    private EntityManager em;

    @SuppressWarnings("unchecked")
    public TenantOffersResultDto list(UUID tenantId) {
        List<OfferRestaurantRefDto> restaurants = new ArrayList<>();
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
            SELECT id, name, city FROM restaurants WHERE tenant_id = :t AND deleted_at IS NULL ORDER BY name
            """).setParameter("t", tenantId).getResultList()) {
            restaurants.add(new OfferRestaurantRefDto((UUID) r[0], (String) r[1], (String) r[2]));
        }

        List<TenantOfferDto> offers = new ArrayList<>();
        for (Object[] o : (List<Object[]>) em.createNativeQuery("""
            SELECT o.id, o.title, o.description, o.type, o.pts, o.image, o.enabled, o.is_pinned,
                   o.starts_at, o.expires_at, o.push_notify, o.segments, o.created_at,
                   o.restaurant_id, rs.name, rs.city, COALESCE(imp.cnt, 0)
              FROM offers o
              JOIN restaurants rs ON rs.id = o.restaurant_id
              LEFT JOIN (
                SELECT offer_id, COUNT(*) AS cnt FROM offer_impressions GROUP BY offer_id
              ) imp ON imp.offer_id = o.id
             WHERE rs.tenant_id = :t AND o.deleted_at IS NULL
             ORDER BY o.created_at DESC
            """).setParameter("t", tenantId).getResultList()) {
            offers.add(new TenantOfferDto(
                (UUID) o[0], (String) o[1], (String) o[2], (String) o[3],
                o[4] == null ? null : ((Number) o[4]).intValue(), (String) o[5],
                bool(o[6]), bool(o[7]), toInstant(o[8]), toInstant(o[9]), bool(o[10]),
                toStringList(o[11]), toInstant(o[12]),
                (UUID) o[13], (String) o[14], (String) o[15],
                o[16] == null ? 0L : ((Number) o[16]).longValue(), 0L));
        }

        return new TenantOffersResultDto(offers, summarize(offers, Instant.now()), restaurants);
    }

    /**
     * Résumé d'une liste d'offres — fonction pure, testable. Une offre est : {@code expired} si
     * {@code expiresAt < now} ; sinon {@code scheduled} si {@code startsAt > now} ; sinon {@code active}
     * si {@code enabled}. (Mêmes règles que le legacy.)
     */
    public static TenantOffersSummaryDto summarize(List<TenantOfferDto> offers, Instant now) {
        long active = 0, scheduled = 0, expired = 0;
        for (TenantOfferDto o : offers) {
            if (o.expiresAt() != null && o.expiresAt().isBefore(now)) {
                expired++;
            } else if (o.startsAt() != null && o.startsAt().isAfter(now)) {
                scheduled++;
            } else if (o.isActive()) {
                active++;
            }
        }
        return new TenantOffersSummaryDto(offers.size(), active, scheduled, expired);
    }

    private static boolean bool(Object o) {
        return o instanceof Boolean b && b;
    }

    /** Convertit une colonne SQL {@code text[]} en {@code List<String>} (défensif). */
    private static List<String> toStringList(Object o) {
        if (o == null) return List.of();
        if (o instanceof String[] arr) return List.of(arr);
        if (o instanceof Array sql) {
            try {
                Object raw = sql.getArray();
                if (raw instanceof Object[] objs) {
                    return Arrays.stream(objs).filter(x -> x != null).map(String::valueOf).toList();
                }
            } catch (SQLException ignored) {
                return List.of();
            }
        }
        return List.of();
    }
}
