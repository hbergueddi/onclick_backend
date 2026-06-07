package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.exception.UnprocessableException;
import com.onesley.oneclick.modules.loyalty.api.PunchCardDto;
import com.onesley.oneclick.security.SecurityHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires isolés (Mockito) de {@link PunchCardService} (PCC Lot 3).
 *
 * <ul>
 *   <li><b>punch</b> upsert (+1 nouvelle carte / +1 carte existante)</li>
 *   <li><b>redeem</b> au palier OK + sous-palier → 422 + non-staff → 403 + introuvable → 404</li>
 *   <li><b>mapping</b> {@code resource_type → activity} (mappé / non mappé / null)</li>
 *   <li><b>listMyCards</b> self-scope (tenant résolu via UserDirectoryApi ; null → vide)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class PunchCardServiceTest {

    @Mock PunchCardRepository repo;
    @Mock UserDirectoryApi userDirectory;

    /** Service construit manuellement (champs finaux via @RequiredArgsConstructor). */
    private PunchCardService service() {
        return new PunchCardService(repo, userDirectory);
    }

    private PunchCard card(UUID tenant, UUID client, String activity, int count, int redeemed) {
        PunchCard c = new PunchCard(UUID.randomUUID(), tenant, client, activity);
        c.setCountPunched(count);
        c.setRedeemedCount(redeemed);
        return c;
    }

    // ─── Mapping resource_type → activity ────────────────────────────────────

    @Test
    void resolveActivity_mapsKnownTypes() {
        assertThat(PunchCardService.resolveActivity("padel_court")).contains("padel");
        assertThat(PunchCardService.resolveActivity("tennis_court")).contains("tennis");
        assertThat(PunchCardService.resolveActivity("spa_room")).contains("spa");
        assertThat(PunchCardService.resolveActivity("golf_tee")).contains("golf");
        assertThat(PunchCardService.resolveActivity("barber_chair")).contains("coiffeur");
        assertThat(PunchCardService.resolveActivity("coach_session")).contains("palm_gym");
    }

    @Test
    void resolveActivity_unknownOrNull_isEmpty() {
        assertThat(PunchCardService.resolveActivity("seminar_room")).isEmpty();
        assertThat(PunchCardService.resolveActivity("unknown_type")).isEmpty();
        assertThat(PunchCardService.resolveActivity(null)).isEmpty();
    }

    // ─── punch (upsert +1) ────────────────────────────────────────────────────

    @Test
    void punch_newCard_createsWithCountOne() {
        PunchCardService svc = service();
        UUID tenant = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        when(repo.findByTenantIdAndClientIdAndActivity(tenant, client, "padel")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        ArgumentCaptor<PunchCard> cap = ArgumentCaptor.forClass(PunchCard.class);
        PunchCardDto dto = svc.punch(tenant, client, "padel");

        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getCountPunched()).isEqualTo(1);
        assertThat(cap.getValue().getActivity()).isEqualTo("padel");
        assertThat(cap.getValue().getLastPunchedAt()).isNotNull();
        assertThat(dto.countPunched()).isEqualTo(1);
        assertThat(dto.remaining()).isEqualTo(9); // 10 - 1
    }

    @Test
    void punch_existingCard_incrementsByOne() {
        PunchCardService svc = service();
        UUID tenant = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        PunchCard existing = card(tenant, client, "spa", 4, 0);
        when(repo.findByTenantIdAndClientIdAndActivity(tenant, client, "spa")).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        PunchCardDto dto = svc.punch(tenant, client, "spa");

        assertThat(existing.getCountPunched()).isEqualTo(5);
        assertThat(dto.countPunched()).isEqualTo(5);
        assertThat(dto.remaining()).isEqualTo(5);
    }

    @Test
    void punch_nullArgs_throws() {
        PunchCardService svc = service();
        assertThatThrownBy(() -> svc.punch(null, UUID.randomUUID(), "padel"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> svc.punch(UUID.randomUUID(), null, "padel"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> svc.punch(UUID.randomUUID(), UUID.randomUUID(), null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── redeem (staff applique une séance gratuite) ────────────────────────────

    @Test
    void redeem_atPalier_succeeds_andIncrementsRedeemed() {
        PunchCardService svc = service();
        // 10 punches, 0 redeemed → 1 palier complet dispo.
        PunchCard c = card(UUID.randomUUID(), UUID.randomUUID(), "padel", 10, 0);
        when(repo.findById(c.getId())).thenReturn(Optional.of(c));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
            PunchCardDto dto = svc.redeem(c.getId());
            assertThat(dto.redeemedCount()).isEqualTo(1);
            assertThat(c.getLastRedeemedAt()).isNotNull();
            // après redeem : palier consommé → remaining repart à 10.
            assertThat(dto.remaining()).isEqualTo(10);
        }
    }

    @Test
    void redeem_belowPalier_throws422() {
        PunchCardService svc = service();
        // 7 punches, 0 redeemed → pas de palier complet (< 10).
        PunchCard c = card(UUID.randomUUID(), UUID.randomUUID(), "padel", 7, 0);
        when(repo.findById(c.getId())).thenReturn(Optional.of(c));

        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
            assertThatThrownBy(() -> svc.redeem(c.getId()))
                .isInstanceOf(UnprocessableException.class);
        }
        assertThat(c.getRedeemedCount()).isZero(); // inchangé
    }

    @Test
    void redeem_secondPalierAlreadyConsumed_throws422() {
        PunchCardService svc = service();
        // 12 punches mais 1 déjà redeemed (palier 1 consommé) → 12 - 1*10 = 2 < 10 → 422.
        PunchCard c = card(UUID.randomUUID(), UUID.randomUUID(), "padel", 12, 1);
        when(repo.findById(c.getId())).thenReturn(Optional.of(c));

        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
            assertThatThrownBy(() -> svc.redeem(c.getId()))
                .isInstanceOf(UnprocessableException.class);
        }
    }

    @Test
    void redeem_secondFullPalier_succeeds() {
        PunchCardService svc = service();
        // 20 punches, 1 redeemed → 20 - 1*10 = 10 ≥ 10 → 2e palier dispo.
        PunchCard c = card(UUID.randomUUID(), UUID.randomUUID(), "padel", 20, 1);
        when(repo.findById(c.getId())).thenReturn(Optional.of(c));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
            PunchCardDto dto = svc.redeem(c.getId());
            assertThat(dto.redeemedCount()).isEqualTo(2);
        }
    }

    @Test
    void redeem_notStaff_throws403() {
        PunchCardService svc = service();
        UUID cardId = UUID.randomUUID();
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(false); // simple membre
            assertThatThrownBy(() -> svc.redeem(cardId))
                .isInstanceOf(ForbiddenException.class);
        }
        // Le membre est refusé AVANT toute lecture DB.
        verify(repo, never()).findById(any());
    }

    @Test
    void redeem_cardNotFound_throws404() {
        PunchCardService svc = service();
        UUID cardId = UUID.randomUUID();
        when(repo.findById(cardId)).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
            assertThatThrownBy(() -> svc.redeem(cardId))
                .isInstanceOf(NotFoundException.class);
        }
    }

    // ─── listMyCards self-scope ────────────────────────────────────────────────

    @Test
    void listMyCards_returnsClientCardsInTenant() {
        PunchCardService svc = service();
        UUID client = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        when(userDirectory.tenantIdById(client)).thenReturn(Optional.of(tenant));
        when(repo.findByTenantIdAndClientIdOrderByActivityAsc(tenant, client))
            .thenReturn(List.of(card(tenant, client, "padel", 3, 0), card(tenant, client, "spa", 1, 0)));

        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(client);
            assertThat(svc.listMyCards()).hasSize(2);
        }
    }

    @Test
    void listMyCards_noTenant_returnsEmpty() {
        PunchCardService svc = service();
        UUID client = UUID.randomUUID();
        when(userDirectory.tenantIdById(client)).thenReturn(Optional.empty()); // user global / OneClick standard

        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(client);
            assertThat(svc.listMyCards()).isEmpty();
        }
        verify(repo, never()).findByTenantIdAndClientIdOrderByActivityAsc(any(), any());
    }

    @Test
    void listMyCards_notAuthenticated_throws403() {
        PunchCardService svc = service();
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(null);
            assertThatThrownBy(svc::listMyCards).isInstanceOf(ForbiddenException.class);
        }
    }

    // ─── listByTenant (export STAFF — Gap #3) ───────────────────────────────────

    @Test
    void listByTenant_mapsCardsWithResolvedNames() {
        PunchCardService svc = service();
        UUID tenant = UUID.randomUUID();
        UUID c1 = UUID.randomUUID();
        when(repo.findByTenantIdOrderByActivityAscClientIdAsc(tenant))
            .thenReturn(List.of(card(tenant, c1, "padel", 7, 0)));
        when(userDirectory.namesByIds(List.of(c1))).thenReturn(List.of(
            new UserDirectoryApi.UserName(c1, "Sofia", "Alami", "0600", "s@x.ma", null)));

        var rows = svc.listByTenant(tenant);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).clientName()).isEqualTo("Sofia Alami");
        assertThat(rows.get(0).phone()).isEqualTo("0600");
        assertThat(rows.get(0).activity()).isEqualTo("padel");
        assertThat(rows.get(0).countPunched()).isEqualTo(7);
    }

    @Test
    void listByTenant_empty_returnsEmpty_noNameLookup() {
        PunchCardService svc = service();
        UUID tenant = UUID.randomUUID();
        when(repo.findByTenantIdOrderByActivityAscClientIdAsc(tenant)).thenReturn(List.of());
        assertThat(svc.listByTenant(tenant)).isEmpty();
        verify(userDirectory, never()).namesByIds(any());
    }
}
