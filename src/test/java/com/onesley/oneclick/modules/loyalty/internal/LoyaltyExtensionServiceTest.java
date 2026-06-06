package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyExtensionDtos.ClientScoreConfigPatchDto;
import com.onesley.oneclick.security.SecurityHelper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link LoyaltyExtensionService} (L3 — modules.loyalty).
 * Ratings/scores, rate-limit AI (reset 24h), restitutions, tier status, vues admin
 * expired/distributions (native SQL + RBAC staff).
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class LoyaltyExtensionServiceTest {

    @Mock ClientRatingRepository ratingRepo;
    @Mock AIUsageRepository aiUsageRepo;
    @Mock RestaurantRestitutionRepository restitutionRepo;
    @Mock RedemptionRepository redemptionRepo;
    @Mock RestaurantTierStatusRepository tierStatusRepo;
    @Mock ClientScoreConfigRepository scoreConfigRepo;
    @Mock UserDirectoryApi userDirectory;
    @Mock EntityManager em;
    @Mock Query query;
    @InjectMocks LoyaltyExtensionService service;

    private final UUID resto = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "em", em);
        lenient().when(em.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
        lenient().when(ratingRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(aiUsageRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(restitutionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        // Config de notation par défaut (les tests qui valident la config l'overrident).
        lenient().when(scoreConfigRepo.findFirstByOrderByCreatedAtAsc())
            .thenReturn(Optional.of(new ClientScoreConfig()));
    }

    private ClientRating rating(String visible) {
        ClientRating r = new ClientRating();
        r.setUserId(user);
        r.setRating(new BigDecimal(visible));
        r.setVisibleRating(new BigDecimal(visible));
        return r;
    }

    // ─── ratings / score ─────────────────────────────────────────────────────

    @Test
    void findUserRatings_maps() {
        when(ratingRepo.findByUser(user)).thenReturn(List.of(rating("5.0")));
        assertThat(service.findUserRatings(user)).hasSize(1);
    }

    @Test
    void computeUserScore_noRatings_defaultsFive() {
        when(ratingRepo.averageVisibleRating(user)).thenReturn(null);
        when(ratingRepo.countByUser(user)).thenReturn(null);
        var s = service.computeUserScore(user);
        assertThat(s.averageRating()).isEqualByComparingTo("5.0");
        assertThat(s.score()).isEqualByComparingTo("100.00");
    }

    @Test
    void computeUserScore_withRatings() {
        when(ratingRepo.averageVisibleRating(user)).thenReturn(4.0);
        when(ratingRepo.countByUser(user)).thenReturn(10L);
        var s = service.computeUserScore(user);
        assertThat(s.averageRating()).isEqualByComparingTo("4.00");
        assertThat(s.score()).isEqualByComparingTo("80.00");
    }

    @Test
    void computeUserScore_enrichesLabelAndReservationCounts() {
        when(ratingRepo.averageVisibleRating(user)).thenReturn(5.0);
        when(ratingRepo.countByUser(user)).thenReturn(8L);
        // Compteurs réservations (total, honorées, no-shows) via SQL natif mocké.
        when(query.getSingleResult()).thenReturn(new Object[]{10L, 8L, 1L});
        var s = service.computeUserScore(user);
        assertThat(s.totalReservations()).isEqualTo(10L);
        assertThat(s.honorees()).isEqualTo(8L);
        assertThat(s.noShows()).isEqualTo(1L);
        assertThat(s.stars()).isEqualByComparingTo("5.00");
        assertThat(s.label()).isEqualTo("Excellent"); // score 100 ≥ seuil_excellent 95, total ≥ min
    }

    @Test
    void computeUserScore_newClient_belowMinReservations_labelsNouveau() {
        when(ratingRepo.averageVisibleRating(user)).thenReturn(5.0);
        when(ratingRepo.countByUser(user)).thenReturn(1L);
        when(query.getSingleResult()).thenReturn(new Object[]{1L, 1L, 0L}); // total 1 < min 3
        var s = service.computeUserScore(user);
        assertThat(s.label()).isEqualTo("Nouveau");
    }

    @Test
    void recordRating_firstRating_startsAtFive_appliesDelta() {
        when(ratingRepo.findByUser(user)).thenReturn(List.of());
        service.recordRating(user, UUID.randomUUID(), new BigDecimal("-0.5"), "no_show");
        ArgumentCaptor<ClientRating> cap = ArgumentCaptor.forClass(ClientRating.class);
        verify(ratingRepo).save(cap.capture());
        assertThat(cap.getValue().getRating()).isEqualByComparingTo("4.5");
    }

    @Test
    void recordRating_existing_usesVisibleRating() {
        when(ratingRepo.findByUser(user)).thenReturn(List.of(rating("3.0")));
        service.recordRating(user, UUID.randomUUID(), new BigDecimal("1.0"), "honored");
        ArgumentCaptor<ClientRating> cap = ArgumentCaptor.forClass(ClientRating.class);
        verify(ratingRepo).save(cap.capture());
        assertThat(cap.getValue().getRating()).isEqualByComparingTo("4.0");
    }

    @Test
    void recordRating_clampsToFiveMax() {
        when(ratingRepo.findByUser(user)).thenReturn(List.of(rating("4.8")));
        service.recordRating(user, UUID.randomUUID(), new BigDecimal("1.0"), "honored");
        ArgumentCaptor<ClientRating> cap = ArgumentCaptor.forClass(ClientRating.class);
        verify(ratingRepo).save(cap.capture());
        assertThat(cap.getValue().getRating()).isEqualByComparingTo("5.0");
    }

    // ─── AI usage ──────────────────────────────────────────────────────────────

    @Test
    void findUsage_noRecord_returnsDto() {
        when(aiUsageRepo.findByUserId(user)).thenReturn(Optional.empty());
        assertThat(service.findUsage(user)).isNotNull();
    }

    @Test
    void findUsage_resetsAfter24h() {
        AIUsage u = new AIUsage();
        u.setUserId(user);
        u.setPromptCount(5);
        u.setLastPromptAt(Instant.now().minusSeconds(90_000)); // > 24h
        when(aiUsageRepo.findByUserId(user)).thenReturn(Optional.of(u));
        service.findUsage(user);
        assertThat(u.getPromptCount()).isZero();
        verify(aiUsageRepo).save(u);
    }

    @Test
    void incrementUsage_newRecord_countsOne() {
        when(aiUsageRepo.findByUserId(user)).thenReturn(Optional.empty());
        var dto = service.incrementUsage(user);
        assertThat(dto).isNotNull();
        ArgumentCaptor<AIUsage> cap = ArgumentCaptor.forClass(AIUsage.class);
        verify(aiUsageRepo).save(cap.capture());
        assertThat(cap.getValue().getPromptCount()).isEqualTo(1);
    }

    @Test
    void incrementUsage_existingAfter24h_resetsThenIncrements() {
        AIUsage u = new AIUsage();
        u.setUserId(user);
        u.setPromptCount(9);
        u.setLastPromptAt(Instant.now().minusSeconds(90_000));
        when(aiUsageRepo.findByUserId(user)).thenReturn(Optional.of(u));
        service.incrementUsage(user);
        assertThat(u.getPromptCount()).isEqualTo(1); // reset 0 puis +1
    }

    // ─── restitutions / tier ───────────────────────────────────────────────────

    @Test
    void createRestitution_andList() {
        assertThat(service.createRestitution(resto, new BigDecimal("100.00"), 50, "geste commercial")).isNotNull();
        when(restitutionRepo.findByRestaurant(resto)).thenReturn(List.of());
        assertThat(service.findRestaurantRestitutions(resto)).isEmpty();
    }

    @Test
    void findRestitutionsByRestaurants_batchMapsRows() {
        RestaurantRestitution r = new RestaurantRestitution();
        r.setRestaurantId(resto);
        r.setAmount(new BigDecimal("75.00"));
        r.setPoints(150);
        r.setStatus("pending");
        when(restitutionRepo.findByRestaurants(List.of(resto))).thenReturn(List.of(r));
        var out = service.findRestitutionsByRestaurants(List.of(resto));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).restaurantId()).isEqualTo(resto);
        assertThat(out.get(0).points()).isEqualTo(150);
    }

    @Test
    void findRestitutionsByRestaurants_emptyIds_returnsEmpty_noQuery() {
        assertThat(service.findRestitutionsByRestaurants(List.of())).isEmpty();
        verify(restitutionRepo, never()).findByRestaurants(any());
    }

    @Test
    void getRestaurantTier_default_whenAbsent() {
        when(tierStatusRepo.findByRestaurantId(resto)).thenReturn(Optional.empty());
        assertThat(service.getRestaurantTier(resto)).isNotNull();
    }

    // ─── admin views (native SQL + RBAC) ────────────────────────────────────────

    @Test
    void findExpiredPointsAdmin_notAdminNoCaller_throwsForbidden() {
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isAdmin).thenReturn(false);
            sec.when(SecurityHelper::currentUserId).thenReturn(null);
            assertThatThrownBy(() -> service.findExpiredPointsAdmin(resto, 10)).isInstanceOf(ForbiddenException.class);
        }
    }

    @Test
    void findExpiredPointsAdmin_staffNoRestaurantId_throwsBadRequest() {
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isAdmin).thenReturn(false);
            sec.when(SecurityHelper::currentUserId).thenReturn(user);
            assertThatThrownBy(() -> service.findExpiredPointsAdmin(null, 10)).isInstanceOf(BadRequestException.class);
        }
    }

    @Test
    void findExpiredPointsAdmin_staffNotOfRestaurant_throwsForbidden() {
        when(query.getSingleResult()).thenReturn(0L);
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isAdmin).thenReturn(false);
            sec.when(SecurityHelper::currentUserId).thenReturn(user);
            assertThatThrownBy(() -> service.findExpiredPointsAdmin(resto, 10)).isInstanceOf(ForbiddenException.class);
        }
    }

    @Test
    void findExpiredPointsAdmin_admin_mapsRows_enrichesNameViaDirectory() {
        // P2.b — la requête ne renvoie plus le nom (5 cols : clientId, pts, createdAt,
        // restaurantId, r.name) ; le nom client vient de UserDirectoryApi (plus de JOIN users).
        UUID clientId = UUID.randomUUID();
        Object[] row = { clientId, 30, Instant.now(), resto, "Resto" };
        when(query.getResultList()).thenReturn(Collections.singletonList(row));
        when(userDirectory.namesByIds(any())).thenReturn(List.of(
            new UserDirectoryApi.UserName(clientId, "Ada", "L", null, null, null)));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isAdmin).thenReturn(true);
            var out = service.findExpiredPointsAdmin(resto, 10);
            assertThat(out).hasSize(1);
            assertThat(out.get(0).userName()).isEqualTo("Ada L");
            assertThat(out.get(0).pointsExpired()).isEqualTo(30);
            assertThat(out.get(0).restaurantName()).isEqualTo("Resto");
        }
    }

    @Test
    void findExpiredPointsAdmin_unknownClient_fallsBackToUnknown() {
        // Parité avec l'ancien COALESCE(... , 'Unknown') : nom absent du directory → "Unknown".
        Object[] row = { UUID.randomUUID(), 10, Instant.now(), resto, "Resto" };
        when(query.getResultList()).thenReturn(Collections.singletonList(row));
        when(userDirectory.namesByIds(any())).thenReturn(List.of());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isAdmin).thenReturn(true);
            var out = service.findExpiredPointsAdmin(resto, 10);
            assertThat(out).hasSize(1);
            assertThat(out.get(0).userName()).isEqualTo("Unknown");
        }
    }

    @Test
    void findPointDistributions_mapsRows_withFilters() {
        // #4 — 8 colonnes : +balance (remainingPoints) +created_by (creditedBy).
        UUID creditor = UUID.randomUUID();
        Object[] row = { UUID.randomUUID(), UUID.randomUUID(), resto, 25, "snap2earn", Instant.now(), 180, creditor };
        when(query.getResultList()).thenReturn(Collections.singletonList(row));
        var out = service.findPointDistributions(resto, user, 10);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).remainingPoints()).isEqualTo(180);
        assertThat(out.get(0).creditedBy()).isEqualTo(creditor);
    }

    @Test
    void restaurantCreditSummary_mapsTotalsAndByMember() {
        // B2 — agrégat crédit resto : Q1 totaux (accordé/consommé/dispo), Q2 byMember.
        UUID member = UUID.randomUUID();
        when(query.getSingleResult()).thenReturn(new Object[]{ 500L, 120L, 380L });
        // singletonList (PAS List.of) : List.of(Object[]) déplie le tableau en varargs.
        when(query.getResultList()).thenReturn(Collections.singletonList(new Object[]{ member, 300L }));
        var dto = service.restaurantCreditSummary(resto);
        assertThat(dto.restaurantId()).isEqualTo(resto);
        assertThat(dto.creditAccorde()).isEqualTo(500L);
        assertThat(dto.creditConsomme()).isEqualTo(120L);
        assertThat(dto.creditDispo()).isEqualTo(380L);
        assertThat(dto.byMember()).hasSize(1);
        assertThat(dto.byMember().get(0).userId()).isEqualTo(member);
        assertThat(dto.byMember().get(0).points()).isEqualTo(300L);
    }

    @Test
    void tierDistribution_mapsRows() {
        Object[] row = { UUID.randomUUID(), UUID.randomUUID(), "Ruby", 0, 42L };
        when(query.getResultList()).thenReturn(Collections.singletonList(row));
        var out = service.tierDistribution();
        assertThat(out).hasSize(1);
        assertThat(out.get(0).name()).isEqualTo("Ruby");
        assertThat(out.get(0).minPoints()).isEqualTo(0);
        assertThat(out.get(0).memberCount()).isEqualTo(42L);
    }

    // ─── score config (singleton V52) ──────────────────────────────────────────

    @Test
    void getScoreConfig_createsDefaultWhenAbsent() {
        when(scoreConfigRepo.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        when(scoreConfigRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        var dto = service.getScoreConfig();
        assertThat(dto.minReservations()).isEqualTo(3);
        assertThat(dto.scoreInitial()).isEqualByComparingTo("5.0");
        assertThat(dto.fenetreMois()).isEqualTo(6);
    }

    @Test
    void updateScoreConfig_appliesPresentFieldsOnly() {
        ClientScoreConfig c = new ClientScoreConfig();
        when(scoreConfigRepo.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(c));
        when(scoreConfigRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        var patch = new ClientScoreConfigPatchDto(
            7, null, null, null, null, null, null, null, 12);
        var dto = service.updateScoreConfig(patch);
        assertThat(dto.minReservations()).isEqualTo(7);   // patché
        assertThat(dto.fenetreMois()).isEqualTo(12);       // patché
        assertThat(dto.honoreesPourRemonter()).isEqualTo(5); // défaut préservé
    }

    // ─── Audit listings : restitutions (paginé + ABAC) ──────────────────────────

    private RestaurantRestitution restitution(UUID rid, String amount, int points, String status) {
        RestaurantRestitution r = new RestaurantRestitution();
        r.setRestaurantId(rid);
        r.setAmount(new BigDecimal(amount));
        r.setPoints(points);
        r.setStatus(status);
        return r;
    }

    @Test
    void findRestitutionsAudit_admin_mapsRow_enrichesRestaurantName() {
        // Admin scope = null → query native count + rows (entité), puis read-view noms resto.
        when(em.createNativeQuery(anyString(), eq(RestaurantRestitution.class))).thenReturn(query);
        when(query.setFirstResult(anyInt())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(1L); // count
        when(query.getResultList())
            .thenReturn(Collections.singletonList(restitution(resto, "100.00", 50, "paid"))) // rows (entité)
            .thenReturn(Collections.singletonList(new Object[]{ resto, "Le Resto" }));         // restaurantNames()

        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isAdmin).thenReturn(true);
            var out = service.findRestitutionsAudit(resto, null, null, 0, 20);
            assertThat(out.totalElements()).isEqualTo(1);
            assertThat(out.content()).hasSize(1);
            assertThat(out.content().get(0).restaurantId()).isEqualTo(resto);
            assertThat(out.content().get(0).restaurantName()).isEqualTo("Le Resto");
            assertThat(out.content().get(0).points()).isEqualTo(50);
        }
    }

    @Test
    void findRestitutionsAudit_owner_scopedToStaffRestaurants() {
        when(em.createNativeQuery(anyString(), eq(RestaurantRestitution.class))).thenReturn(query);
        when(query.setFirstResult(anyInt())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(1L); // count (scope non vide)
        when(query.getResultList())
            .thenReturn(Collections.singletonList(resto))                                      // adminOrNull() : scope staff
            .thenReturn(Collections.singletonList(restitution(resto, "75.00", 30, "pending"))) // rows
            .thenReturn(Collections.singletonList(new Object[]{ resto, "Le Resto" }));         // restaurantNames()

        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isAdmin).thenReturn(false);
            sec.when(SecurityHelper::currentUserId).thenReturn(user);
            var out = service.findRestitutionsAudit(null, null, null, 0, 20);
            assertThat(out.content()).hasSize(1);
            assertThat(out.content().get(0).restaurantName()).isEqualTo("Le Resto");
        }
    }

    @Test
    void findRestitutionsAudit_clientWithoutStaff_returnsEmpty_noCountQuery() {
        when(query.getResultList()).thenReturn(Collections.emptyList()); // aucun resto staff
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isAdmin).thenReturn(false);
            sec.when(SecurityHelper::currentUserId).thenReturn(user);
            var out = service.findRestitutionsAudit(null, null, null, 0, 20);
            assertThat(out.content()).isEmpty();
            assertThat(out.totalElements()).isZero();
        }
        // scope vide → on ne lance jamais le COUNT.
        verify(query, never()).getSingleResult();
    }

    @Test
    void findRestitutionsAudit_unauthenticated_throwsForbidden() {
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isAdmin).thenReturn(false);
            sec.when(SecurityHelper::currentUserId).thenReturn(null);
            assertThatThrownBy(() -> service.findRestitutionsAudit(null, null, null, 0, 20))
                .isInstanceOf(ForbiddenException.class);
        }
    }

    // ─── Audit listings : redemptions (paginé natif + ABAC + enrichissement noms) ──

    @Test
    void findRedemptionsAudit_admin_mapsRows_enrichesClientAndRestaurantNames() {
        UUID redemptionId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        // count → 1 ; puis rows.
        when(query.getSingleResult()).thenReturn(1L);
        Object[] row = { redemptionId, accountId, clientId, resto, "Le Resto",
                         200, new BigDecimal("40.00"), Boolean.TRUE, Instant.now() };
        when(query.getResultList()).thenReturn(Collections.singletonList(row));
        // pagination chain (native) doit renvoyer le mock pour ne pas casser le fluent.
        when(query.setFirstResult(anyInt())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(userDirectory.namesByIds(any())).thenReturn(List.of(
            new UserDirectoryApi.UserName(clientId, "Sara", "B", null, null, null)));

        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isAdmin).thenReturn(true);
            var out = service.findRedemptionsAudit(null, null, null, null, 0, 20);
            assertThat(out.totalElements()).isEqualTo(1);
            assertThat(out.content()).hasSize(1);
            var dto = out.content().get(0);
            assertThat(dto.id()).isEqualTo(redemptionId);
            assertThat(dto.clientId()).isEqualTo(clientId);
            assertThat(dto.clientName()).isEqualTo("Sara B");
            assertThat(dto.restaurantId()).isEqualTo(resto);
            assertThat(dto.restaurantName()).isEqualTo("Le Resto");
            assertThat(dto.pointsUsed()).isEqualTo(200);
            assertThat(dto.discountAmount()).isEqualByComparingTo("40.00");
            assertThat(dto.otpValidated()).isTrue();
        }
    }

    @Test
    void findRedemptionsAudit_admin_zeroCount_shortCircuits_noRowFetch() {
        when(query.getSingleResult()).thenReturn(0L); // count = 0
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isAdmin).thenReturn(true);
            var out = service.findRedemptionsAudit(resto, null, null, null, 0, 20);
            assertThat(out.content()).isEmpty();
            assertThat(out.totalElements()).isZero();
        }
        // count-only : pas de fetch des lignes ni de résolution de noms.
        verify(query, never()).getResultList();
        verify(userDirectory, never()).namesByIds(any());
    }

    @Test
    void findRedemptionsAudit_clientWithoutStaff_returnsEmpty_noCountQuery() {
        when(query.getResultList()).thenReturn(Collections.emptyList()); // aucun resto staff
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isAdmin).thenReturn(false);
            sec.when(SecurityHelper::currentUserId).thenReturn(user);
            var out = service.findRedemptionsAudit(null, null, null, null, 0, 20);
            assertThat(out.content()).isEmpty();
        }
        // scope vide → on ne lance jamais le COUNT redemptions.
        verify(query, never()).getSingleResult();
    }

    @Test
    void findRedemptionsAudit_unauthenticated_throwsForbidden() {
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isAdmin).thenReturn(false);
            sec.when(SecurityHelper::currentUserId).thenReturn(null);
            assertThatThrownBy(() -> service.findRedemptionsAudit(null, null, null, null, 0, 20))
                .isInstanceOf(ForbiddenException.class);
        }
    }
}
