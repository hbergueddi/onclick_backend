package com.onesley.oneclick.modules.loyalty.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link PromoAudienceResolver} — sélection du bon SQL par segment + mapping
 * des {@code client_id} + <b>gate de périmètre tenant</b> (tenant à adhésion → seuls les membres
 * actifs ; tenant public {@code oneclick} → aucun filtre). Le SQL natif réel est validé en
 * intégration (cf {@code PromoPushDispatchIntegrationTest}).
 *
 * <p>Le mock {@code em} route {@code createNativeQuery(sql)} vers 3 {@link Query} distincts selon
 * le SQL (segment / résolution tenant du resto / membres du tenant), car {@code resolve()} émet
 * jusqu'à 3 requêtes (segment puis, si audience non vide, les 2 du gate).
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class PromoAudienceResolverTest {

    @Mock EntityManager em;
    @Mock Query segmentQuery;   // requête du segment (all/fideles/…)
    @Mock Query restoQuery;     // SELECT r.tenant_id, t.slug (gate)
    @Mock Query membersQuery;   // SELECT tm.user_id FROM tenant_memberships (gate)
    @InjectMocks PromoAudienceResolver resolver;
    @Captor ArgumentCaptor<String> sqlCaptor;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(resolver, "em", em);
        lenient().when(em.createNativeQuery(anyString())).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            if (sql.contains("FROM restaurants r JOIN tenants")) return restoQuery;
            if (sql.contains("FROM tenant_memberships")) return membersQuery;
            return segmentQuery;
        });
        lenient().when(segmentQuery.setParameter(anyString(), any())).thenReturn(segmentQuery);
        lenient().when(restoQuery.setParameter(anyString(), any())).thenReturn(restoQuery);
        lenient().when(membersQuery.setParameter(anyString(), any())).thenReturn(membersQuery);
        lenient().when(segmentQuery.getResultList()).thenReturn(List.of());
        // Par défaut : resto rattaché au tenant PUBLIC → le gate est un pass-through (aucun filtre).
        lenient().when(restoQuery.getResultList()).thenReturn(List.<Object[]>of(new Object[]{UUID.randomUUID(), "oneclick"}));
        lenient().when(membersQuery.getResultList()).thenReturn(List.of());
    }

    @Test
    void all_usesAccountsOnly_andMapsClientIds() {
        UUID u = UUID.randomUUID();
        when(segmentQuery.getResultList()).thenReturn(List.of(u));   // tenant public par défaut → conservé
        assertThat(resolver.resolve(UUID.randomUUID(), "all")).containsExactly(u);
        verify(em, atLeastOnce()).createNativeQuery(sqlCaptor.capture());
        assertThat(sqlCaptor.getAllValues())
            .anySatisfy(sql -> assertThat(sql).contains("loyalty_accounts").doesNotContain("snap2earn"));
    }

    @Test
    void fideles_uses90dSnap2earnAtLeast3() {
        resolver.resolve(UUID.randomUUID(), "fideles");
        verify(em).createNativeQuery(sqlCaptor.capture());   // audience vide → gate court-circuité (1 seule requête)
        assertThat(sqlCaptor.getValue()).contains("snap2earn").contains("90 days").contains(">= 3");
    }

    @Test
    void nouveaux_usesFirstTxWithin30d() {
        resolver.resolve(UUID.randomUUID(), "nouveaux");
        verify(em).createNativeQuery(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("min(lt.created_at)").contains("30 days");
    }

    @Test
    void inactifs_usesNoSnap2earnIn60d() {
        resolver.resolve(UUID.randomUUID(), "inactifs");
        verify(em).createNativeQuery(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("60 days").contains("= 0");
    }

    @Test
    void tierSegment_joinsTiers_mapsLegacyKeyToCharteName() {
        // La clé legacy « emeraude » pointe désormais vers le palier charte renommé « Signature » (V98).
        resolver.resolve(UUID.randomUUID(), "emeraude");
        verify(em).createNativeQuery(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("tiers").contains("la.tier_id");
        verify(segmentQuery).setParameter("tier", "Signature");
    }

    @Test
    void tierSegment_charteKeyAmbassadeur_bindsCharteName() {
        resolver.resolve(UUID.randomUUID(), "ambassadeur");
        verify(em).createNativeQuery(sqlCaptor.capture());
        verify(segmentQuery).setParameter("tier", "Ambassadeur");
    }

    @Test
    void unknownSegment_andNullRestaurant_returnEmpty_noQuery() {
        assertThat(resolver.resolve(UUID.randomUUID(), "bogus")).isEmpty();
        assertThat(resolver.resolve(null, "all")).isEmpty();
        verify(em, never()).createNativeQuery(anyString());
    }

    // ─── Gate de périmètre tenant (fuite « non-membre reçoit promo PCC ») ───────

    @Test
    void membershipGate_tenantAdhesion_keepsOnlyActiveMembers() {
        UUID member = UUID.randomUUID();
        UUID nonMember = UUID.randomUUID();
        when(segmentQuery.getResultList()).thenReturn(List.of(member, nonMember));
        // resto rattaché à un tenant À ADHÉSION (slug ≠ oneclick)
        when(restoQuery.getResultList()).thenReturn(List.<Object[]>of(new Object[]{UUID.randomUUID(), "palmeraie"}));
        // seul `member` est membre actif du tenant
        when(membersQuery.getResultList()).thenReturn(List.<Object>of(member));

        assertThat(resolver.resolve(UUID.randomUUID(), "all"))
            .as("le non-membre est filtré, seul le membre actif PCC reste")
            .containsExactly(member);
    }

    @Test
    void membershipGate_publicTenant_keepsEveryone() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        when(segmentQuery.getResultList()).thenReturn(List.of(u1, u2));
        when(restoQuery.getResultList()).thenReturn(List.<Object[]>of(new Object[]{UUID.randomUUID(), "oneclick"}));

        assertThat(resolver.resolve(UUID.randomUUID(), "all"))
            .as("tenant public → aucun filtre, tous conservés")
            .containsExactlyInAnyOrder(u1, u2);
        // pas de requête membres pour un tenant public
        verify(membersQuery, never()).getResultList();
    }
}
