package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.TenantClientDtos.TenantClientDto;
import com.onesley.oneclick.modules.analytics.api.TenantDashboardDtos.AlertDto;
import com.onesley.oneclick.modules.analytics.api.TenantDashboardDtos.TenantDashboardDto;
import com.onesley.oneclick.modules.analytics.api.TenantDashboardDtos.TopClientDto;
import com.onesley.oneclick.modules.analytics.api.TenantDashboardDtos.TopRestaurantDto;
import com.onesley.oneclick.modules.analytics.api.TenantDashboardDtos.UpcomingReservationDto;
import com.onesley.oneclick.modules.analytics.api.TenantRestaurantDtos.TenantRestaurantDto;
import com.onesley.oneclick.modules.analytics.api.TenantReservationDtos.TenantReservationDto;
import com.onesley.oneclick.modules.analytics.api.TenantReservationDtos.TenantReservationsResultDto;
import com.onesley.oneclick.modules.analytics.api.TenantReservationDtos.TenantReservationsSummaryDto;
import com.onesley.oneclick.modules.analytics.internal.TenantDashboardService.TierThreshold;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires <b>isolés</b> (Mockito, sans DB) du cockpit tenant-admin {@link TenantDashboardService}.
 *
 * <p>Couvre :
 * <ul>
 *   <li>les helpers <b>purs</b> : {@code deltaPercent} (null si base = 0, % signé sinon),
 *       {@code deriveAlerts} (restos inactifs warning/danger, résas pending, 0 résa, RAS),
 *       {@code tierNameFor} (DB seuils, null si vide, plancher), {@code toUpcoming}
 *       (filtre fenêtre 7j + statuts), {@code dayLabel} ;</li>
 *   <li>{@code compute(...)} avec un {@link EntityManager} et les 3 services réutilisés <b>mockés</b> :
 *       troncature top-5, tier dérivé, mapping des KPIs.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantDashboardServiceTest {

    @Mock EntityManager em;
    @Mock TenantRestaurantsService tenantRestaurantsService;
    @Mock TenantClientsService tenantClientsService;
    @Mock TenantReservationsService tenantReservationsService;
    @InjectMocks TenantDashboardService service;

    @BeforeEach
    void injectEntityManager() {
        // @PersistenceContext est field-injected en prod ; @InjectMocks fait l'injection par
        // constructeur (les 3 services) et ne pose pas le mock EntityManager → on l'injecte ici.
        ReflectionTestUtils.setField(service, "em", em);
    }

    // ─── deltaPercent (long) — fonction pure ────────────────────────────────────────

    @Test
    void deltaPercentLong_nullWhenPrevZeroOrNegative() {
        assertThat(TenantDashboardService.deltaPercent(100L, 0L)).isNull();
        assertThat(TenantDashboardService.deltaPercent(100L, -5L)).isNull();
    }

    @Test
    void deltaPercentLong_signedPercent() {
        assertThat(TenantDashboardService.deltaPercent(150L, 100L)).isEqualTo(50);
        assertThat(TenantDashboardService.deltaPercent(50L, 100L)).isEqualTo(-50);
        assertThat(TenantDashboardService.deltaPercent(100L, 100L)).isEqualTo(0);
    }

    // ─── deltaPercent (BigDecimal) — fonction pure ──────────────────────────────────

    @Test
    void deltaPercentBd_nullWhenPrevZeroOrNull() {
        assertThat(TenantDashboardService.deltaPercent(new BigDecimal("500"), null)).isNull();
        assertThat(TenantDashboardService.deltaPercent(new BigDecimal("500"), BigDecimal.ZERO)).isNull();
    }

    @Test
    void deltaPercentBd_signedPercent() {
        assertThat(TenantDashboardService.deltaPercent(new BigDecimal("1500"), new BigDecimal("1000"))).isEqualTo(50);
        assertThat(TenantDashboardService.deltaPercent(new BigDecimal("500"), new BigDecimal("1000"))).isEqualTo(-50);
        assertThat(TenantDashboardService.deltaPercent(null, new BigDecimal("1000"))).isEqualTo(-100);
    }

    // ─── tierNameFor — fonction pure (table tiers canonique) ────────────────────────

    @Test
    void tierNameFor_nullWhenNoThresholds() {
        assertThat(TenantDashboardService.tierNameFor(5000L, List.of())).isNull();
        assertThat(TenantDashboardService.tierNameFor(5000L, null)).isNull();
    }

    @Test
    void tierNameFor_picksHighestThresholdAtOrBelowPoints() {
        List<TierThreshold> tiers = List.of(
            new TierThreshold(200, "Connaisseur"),
            new TierThreshold(500, "Grand Cru"),
            new TierThreshold(1000, "Signature"),
            new TierThreshold(10000, "Ambassadeur"),
            new TierThreshold(50000, "Table Secrète"));
        assertThat(TenantDashboardService.tierNameFor(700L, tiers)).isEqualTo("Grand Cru");
        assertThat(TenantDashboardService.tierNameFor(1000L, tiers)).isEqualTo("Signature");
        assertThat(TenantDashboardService.tierNameFor(60000L, tiers)).isEqualTo("Table Secrète");
    }

    @Test
    void tierNameFor_belowFloor_returnsLowestTier() {
        List<TierThreshold> tiers = List.of(
            new TierThreshold(200, "Connaisseur"),
            new TierThreshold(500, "Grand Cru"));
        assertThat(TenantDashboardService.tierNameFor(50L, tiers)).isEqualTo("Connaisseur");
    }

    // ─── deriveAlerts — fonction pure ───────────────────────────────────────────────

    @Test
    void deriveAlerts_inactiveRestaurants_warningThenDanger() {
        List<AlertDto> warn = TenantDashboardService.deriveAlerts(5, 4, List.of(), 10);
        assertThat(warn).anySatisfy(a -> {
            assertThat(a.type()).isEqualTo("warning");
            assertThat(a.title()).contains("1 restaurant");
        });

        List<AlertDto> danger = TenantDashboardService.deriveAlerts(5, 1, List.of(), 10);
        assertThat(danger).anySatisfy(a -> assertThat(a.type()).isEqualTo("danger"));
    }

    @Test
    void deriveAlerts_pendingUpcoming_warning() {
        UpcomingReservationDto pending = new UpcomingReservationDto(
            UUID.randomUUID(), "Resto", "Client", "2026-06-25", "20:00", 2, "pending");
        UpcomingReservationDto confirmed = new UpcomingReservationDto(
            UUID.randomUUID(), "Resto", "Client", "2026-06-25", "21:00", 2, "confirmed");
        List<AlertDto> alerts = TenantDashboardService.deriveAlerts(3, 3, List.of(pending, confirmed), 5);
        assertThat(alerts).anySatisfy(a -> {
            assertThat(a.type()).isEqualTo("warning");
            assertThat(a.title()).contains("1 réservation");
        });
    }

    @Test
    void deriveAlerts_noReservations_infoAlert() {
        List<AlertDto> alerts = TenantDashboardService.deriveAlerts(3, 3, List.of(), 0);
        assertThat(alerts).anySatisfy(a -> {
            assertThat(a.type()).isEqualTo("info");
            assertThat(a.title()).contains("Aucune réservation");
        });
    }

    @Test
    void deriveAlerts_allGreen_singleInfo() {
        List<AlertDto> alerts = TenantDashboardService.deriveAlerts(3, 3, List.of(), 12);
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).type()).isEqualTo("info");
        assertThat(alerts.get(0).icon()).isEqualTo("check");
    }

    // ─── toUpcoming — fonction pure (fenêtre 7j + statuts à confirmer) ───────────────

    @Test
    void toUpcoming_filtersWindowAndStatuses_andFormats() {
        Instant now = Instant.parse("2026-06-22T10:00:00Z");
        TenantReservationDto past = resa("2026-06-21T20:00:00Z", "confirmed");           // passée → exclue
        TenantReservationDto tooFar = resa("2026-07-05T20:00:00Z", "confirmed");          // > 7j → exclue
        TenantReservationDto cancelled = resa("2026-06-23T20:00:00Z", "cancelled");       // statut exclu
        TenantReservationDto ok1 = resa("2026-06-24T19:30:00Z", "pending");
        TenantReservationDto ok2 = resa("2026-06-23T12:15:00Z", "counter_proposed");

        List<UpcomingReservationDto> out = TenantDashboardService.toUpcoming(
            List.of(past, tooFar, cancelled, ok1, ok2), now);

        assertThat(out).hasSize(2);
        // tri chronologique croissant → ok2 (23) avant ok1 (24)
        assertThat(out.get(0).status()).isEqualTo("counter_proposed");
        assertThat(out.get(0).date()).isEqualTo("2026-06-23");
        assertThat(out.get(0).time()).isEqualTo("12:15");
        assertThat(out.get(0).restaurantName()).isEqualTo("Le Resto");
        assertThat(out.get(0).clientName()).isEqualTo("Jean Dupont");
        assertThat(out.get(0).guestsCount()).isEqualTo(4);
        assertThat(out.get(1).status()).isEqualTo("pending");
        assertThat(out.get(1).time()).isEqualTo("19:30");
    }

    @Test
    void toUpcoming_blankClientName_fallsBackToClient() {
        Instant now = Instant.parse("2026-06-22T10:00:00Z");
        TenantReservationDto r = new TenantReservationDto(
            UUID.randomUUID(), Instant.parse("2026-06-23T20:00:00Z"), 2, "confirmed", null,
            now, UUID.randomUUID(), "Le Resto", "Casa", UUID.randomUUID(), null, null, null, null);
        List<UpcomingReservationDto> out = TenantDashboardService.toUpcoming(List.of(r), now);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).clientName()).isEqualTo("Client");
    }

    // ─── dayLabel — fonction pure ───────────────────────────────────────────────────

    @Test
    void dayLabel_formatsDayMonth() {
        assertThat(TenantDashboardService.dayLabel("2026-06-06")).isEqualTo("6/6");
        assertThat(TenantDashboardService.dayLabel("2026-12-25")).isEqualTo("25/12");
    }

    // ─── compute(...) — EntityManager + services réutilisés mockés ──────────────────

    @Test
    void compute_truncatesTopFive_andDerivesTierFromDb() {
        UUID tenantId = UUID.randomUUID();
        stubScalarQueries();

        // tiers: une ligne (Signature à 1000) — palier dérivé backend.
        Query tiersQuery = mock();
        when(tiersQuery.setParameter(eq("t"), any())).thenReturn(tiersQuery);
        when(tiersQuery.getResultList()).thenReturn(List.<Object[]>of(
            new Object[]{"Connaisseur", 200}, new Object[]{"Signature", 1000}));
        when(em.createNativeQuery(org.mockito.ArgumentMatchers.contains("FROM tiers"))).thenReturn(tiersQuery);

        // 6 restos → top 5 attendu (tri par ca30j desc).
        when(tenantRestaurantsService.list(tenantId)).thenReturn(List.of(
            resto("A", "200"), resto("B", "600"), resto("C", "100"),
            resto("D", "900"), resto("E", "50"), resto("F", "300")));

        // 1 client avec 1200 pts → tier "Signature" (≥1000), tickets = visits30j - reservations30j.
        when(tenantClientsService.list(tenantId)).thenReturn(List.of(
            client("Karim", "Benali", "800", 1200, 7, 2)));

        when(tenantReservationsService.list(eq(tenantId), eq(7)))
            .thenReturn(new TenantReservationsResultDto(List.of(), summary(), List.of()));

        TenantDashboardDto dto = service.compute(tenantId);

        assertThat(dto.topRestaurants()).hasSize(5);
        assertThat(dto.topRestaurants().get(0).name()).isEqualTo("D"); // 900 = plus haut CA
        assertThat(dto.topRestaurants()).extracting(TopRestaurantDto::name).doesNotContain("E"); // 50 tronqué

        assertThat(dto.topClients()).hasSize(1);
        TopClientDto c = dto.topClients().get(0);
        assertThat(c.tier()).isEqualTo("Signature");
        assertThat(c.tickets()).isEqualTo(5); // 7 visits - 2 reservations
        assertThat(c.points()).isEqualTo(1200);

        // KPIs scalaires (stubScalarQueries) + delta null car prev=0.
        assertThat(dto.ca30j()).isEqualByComparingTo("1000");
        assertThat(dto.reservations30j()).isEqualTo(12L);
        assertThat(dto.clientsActifs30j()).isEqualTo(8L);
        assertThat(dto.pointsDistribues30j()).isEqualTo(300L);
        assertThat(dto.deltaCa()).isNull();           // prev 0 → null
        assertThat(dto.restaurantsTotal()).isEqualTo(6L);
        assertThat(dto.restaurantsActifs()).isEqualTo(5L);
        assertThat(dto.offresActives()).isEqualTo(3L);
        assertThat(dto.alerts()).isNotEmpty();
    }

    // ─── stubs ──────────────────────────────────────────────────────────────────────

    /** Stube les 4 requêtes scalaires de {@code compute} + la dailyTrend (vide). */
    private void stubScalarQueries() {
        // 1. CA row : [ca30j, ca30jPrev, points30j, points30jPrev]
        stubSingleResult("FROM loyalty_transactions lt",
            new Object[]{new BigDecimal("1000"), BigDecimal.ZERO, 300, 0});
        // 2. réservations row : [30j, prev]
        stubSingleResult("FROM reservations\n", new Object[]{12, 0});
        // 3. clients actifs row : [30j, prev]  (CTE "WITH activity")
        stubSingleResult("WITH activity AS", new Object[]{8, 0});
        // 4. restos row : [total, actifs, offresActives]
        stubSingleResult("(SELECT COUNT(*) FROM restaurants WHERE tenant_id = :t AND deleted_at IS NULL)",
            new Object[]{6, 5, 3});
        // 5. dailyTrend : liste vide (séries non testées ici)
        Query trend = mock();
        when(trend.setParameter(eq("t"), any())).thenReturn(trend);
        when(trend.getResultList()).thenReturn(List.of());
        when(em.createNativeQuery(org.mockito.ArgumentMatchers.contains("WITH days AS"))).thenReturn(trend);
    }

    private void stubSingleResult(String sqlFragment, Object[] row) {
        Query q = mock();
        when(q.setParameter(eq("t"), any())).thenReturn(q);
        when(q.getSingleResult()).thenReturn(row);
        when(em.createNativeQuery(org.mockito.ArgumentMatchers.contains(sqlFragment))).thenReturn(q);
    }

    private static Query mock() { return org.mockito.Mockito.mock(Query.class); }

    private static TenantRestaurantDto resto(String name, String ca) {
        return new TenantRestaurantDto(UUID.randomUUID(), name, "Casa", "Marocaine", null,
            "active", null, null, null, null, new BigDecimal(ca), 3L, 1L, 2L, Instant.now());
    }

    private static TenantClientDto client(String first, String last, String ca30j, long points,
                                          long visits30j, long reservations30j) {
        return new TenantClientDto(UUID.randomUUID(), first, last, "x@x.ma", "+212", "Casa", null,
            new BigDecimal(ca30j), new BigDecimal(ca30j), 10L, visits30j, reservations30j, points,
            Instant.now().minus(40, ChronoUnit.DAYS), Instant.now(), null, null, "actif");
    }

    private static TenantReservationDto resa(String isoAt, String status) {
        return new TenantReservationDto(
            UUID.randomUUID(), Instant.parse(isoAt), 4, status, null, Instant.now(),
            UUID.randomUUID(), "Le Resto", "Casa", UUID.randomUUID(),
            "Jean", "Dupont", "+212", "jean@x.ma");
    }

    private static TenantReservationsSummaryDto summary() {
        return new TenantReservationsSummaryDto(0, Map.of(), Map.of(), 0, 0, 0);
    }
}
