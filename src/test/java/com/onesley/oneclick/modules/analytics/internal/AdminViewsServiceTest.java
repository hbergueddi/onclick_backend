package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.security.SecurityHelper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link AdminViewsService} (L3 — modules.analytics).
 * findAdminUsers (filtres), walletSummary, walletTransactions (RBAC admin/staff),
 * recyclingPool, adminHICockpit — mapping Object[] natif.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class AdminViewsServiceTest {

    @Mock EntityManager em;
    @Mock Query query;
    @InjectMocks AdminViewsService service;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "em", em);
        lenient().when(em.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAdminUsers_filtersAndMaps() {
        Object[] row = { UUID.randomUUID(), "F", "L", "e@x.ma", "0600", "CLIENT",
            UUID.randomUUID(), 100, 5L, 3L, Instant.now() };
        when(query.getResultList()).thenReturn(java.util.Collections.singletonList(row));
        assertThat(service.findAdminUsers("ali", "CLIENT", UUID.randomUUID(), 50)).hasSize(1);
        assertThat(service.findAdminUsers(null, null, null, 50)).hasSize(1);
        assertThat(service.findAdminUsers("  ", null, null, 50)).hasSize(1);
    }

    @Test
    void walletSummary_mapsRow() {
        Object[] row = { new BigDecimal("1000"), new BigDecimal("200"), new BigDecimal("800"), 10L,
            new BigDecimal("50"), new BigDecimal("20") };
        when(query.getSingleResult()).thenReturn(row);
        assertThat(service.walletSummary().totalCredit()).isEqualByComparingTo("1000");
    }

    @Test
    @SuppressWarnings("unchecked")
    void walletTransactions_adminBypass_mapsRows() {
        Object[] row = { UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100"),
            "credit", "commission", Instant.now() };
        when(query.getResultList()).thenReturn(java.util.Collections.singletonList(row));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isAdmin).thenReturn(true);
            assertThat(service.walletTransactions(UUID.randomUUID(), UUID.randomUUID(), 50)).hasSize(1);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void walletTransactions_staffOfRestaurant_ok() {
        Object[] row = { UUID.randomUUID(), null, null, new BigDecimal("100"), "credit", "x", Instant.now() };
        when(query.getResultList()).thenReturn(java.util.Collections.singletonList(row));
        when(query.getSingleResult()).thenReturn(1L); // staff count > 0
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isAdmin).thenReturn(false);
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            assertThat(service.walletTransactions(null, UUID.randomUUID(), 50)).hasSize(1);
        }
    }

    @Test
    void walletTransactions_notAuthenticated_forbidden() {
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isAdmin).thenReturn(false);
            sec.when(SecurityHelper::currentUserId).thenReturn(null);
            assertThatThrownBy(() -> service.walletTransactions(null, UUID.randomUUID(), 50))
                .isInstanceOf(ForbiddenException.class);
        }
    }

    @Test
    void walletTransactions_staffNoRestaurantId_badRequest() {
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isAdmin).thenReturn(false);
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            assertThatThrownBy(() -> service.walletTransactions(null, null, 50))
                .isInstanceOf(BadRequestException.class);
        }
    }

    @Test
    void walletTransactions_notStaff_forbidden() {
        when(query.getSingleResult()).thenReturn(0L); // staff count == 0
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isAdmin).thenReturn(false);
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            assertThatThrownBy(() -> service.walletTransactions(null, UUID.randomUUID(), 50))
                .isInstanceOf(ForbiddenException.class);
        }
    }

    @Test
    void recyclingPool_mapsRow() {
        Object[] row = { new BigDecimal("5000"), new BigDecimal("1000"), new BigDecimal("400"), 12L, new BigDecimal("35") };
        when(query.getSingleResult()).thenReturn(row);
        assertThat(service.recyclingPool().totalPool()).isEqualByComparingTo("5000");
    }

    @Test
    void adminHICockpit_mapsRow() {
        Object[] row = { 100L, 80L, 5L, new BigDecimal("12000"), new BigDecimal("130000"), 42L };
        when(query.getSingleResult()).thenReturn(row);
        assertThat(service.adminHICockpit().restaurantsCount()).isEqualTo(100L);
    }

    // ─── B1 — groupDashboardRollup (anti N+1) ────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void groupDashboardRollup_admin_mergesFiveSources() {
        UUID r = UUID.randomUUID();
        // 5 getResultList consécutifs = les 5 requêtes (résa, tickets, points, wallet, staff).
        when(query.getResultList()).thenReturn(
            java.util.Collections.singletonList(new Object[]{ r, 10L, 4L }),
            java.util.Collections.singletonList(new Object[]{ r, 7L, new BigDecimal("1500.00") }),
            java.util.Collections.singletonList(new Object[]{ r, 250L }),
            java.util.Collections.singletonList(new Object[]{ r, new BigDecimal("44.20") }),
            java.util.Collections.singletonList(new Object[]{ r, 3L })
        );
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isAdmin).thenReturn(true);
            var out = service.groupDashboardRollup(List.of(r));
            assertThat(out).hasSize(1);
            var d = out.get(0);
            assertThat(d.restaurantId()).isEqualTo(r);
            assertThat(d.reservations()).isEqualTo(10L);
            assertThat(d.honored()).isEqualTo(4L);
            assertThat(d.tickets()).isEqualTo(7L);
            assertThat(d.totalCA()).isEqualByComparingTo("1500.00");
            assertThat(d.totalPoints()).isEqualTo(250L);
            assertThat(d.walletBalance()).isEqualByComparingTo("44.20");
            assertThat(d.staff()).isEqualTo(3L);
        }
    }

    @Test
    void groupDashboardRollup_emptyIds_returnsEmpty_noQuery() {
        assertThat(service.groupDashboardRollup(List.of())).isEmpty();
    }

    @Test
    void groupDashboardRollup_nonAdmin_notStaffOfAll_forbidden() {
        // ABAC : caller possède 1 resto sur 2 demandés → 403.
        when(query.getSingleResult()).thenReturn(1L);
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isAdmin).thenReturn(false);
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            assertThatThrownBy(() ->
                service.groupDashboardRollup(List.of(UUID.randomUUID(), UUID.randomUUID())))
                .isInstanceOf(ForbiddenException.class);
        }
    }
}
