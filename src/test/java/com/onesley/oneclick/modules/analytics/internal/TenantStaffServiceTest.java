package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.TenantStaffDtos.TenantStaffMemberDto;
import com.onesley.oneclick.modules.analytics.api.TenantStaffDtos.TenantStaffSummaryDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires isolés des fonctions pures de {@link TenantStaffService} (C4.5) :
 * {@code roleTier} + {@code summarize} (owners/managers/staff/cross-resto). L'agrégat SQL est
 * couvert par l'intégration.
 */
class TenantStaffServiceTest {

    private static TenantStaffMemberDto member(UUID userId, String role, UUID restoId) {
        return new TenantStaffMemberDto(UUID.randomUUID(), userId, "A", "B", null, null, null,
            role, "actif", null, restoId, "Resto");
    }

    @Test
    void roleTier_mapsOwnerManagerStaff() {
        assertThat(TenantStaffService.roleTier("owner")).isEqualTo("owner");
        assertThat(TenantStaffService.roleTier("directeur")).isEqualTo("owner");
        assertThat(TenantStaffService.roleTier("manager")).isEqualTo("manager");
        assertThat(TenantStaffService.roleTier("responsable_resa")).isEqualTo("manager");
        assertThat(TenantStaffService.roleTier("controleur")).isEqualTo("manager");
        assertThat(TenantStaffService.roleTier("serveur")).isEqualTo("staff");
        assertThat(TenantStaffService.roleTier(null)).isEqualTo("staff");
    }

    @Test
    void summarize_empty_isAllZero() {
        TenantStaffSummaryDto s = TenantStaffService.summarize(List.of());
        assertThat(s.total()).isZero();
        assertThat(s.owners()).isZero();
        assertThat(s.managers()).isZero();
        assertThat(s.staff()).isZero();
        assertThat(s.crossResto()).isZero();
    }

    @Test
    void summarize_countsTiersAndCrossResto() {
        UUID u1 = UUID.randomUUID();   // sur 2 restos (cross-resto)
        UUID u2 = UUID.randomUUID();
        UUID u3 = UUID.randomUUID();
        UUID rA = UUID.randomUUID();
        UUID rB = UUID.randomUUID();
        List<TenantStaffMemberDto> rows = List.of(
            member(u1, "owner", rA),
            member(u1, "manager", rB),     // même user, autre resto → cross-resto
            member(u2, "serveur", rA),
            member(u3, "manager", rB)
        );

        TenantStaffSummaryDto s = TenantStaffService.summarize(rows);

        assertThat(s.total()).isEqualTo(4);
        assertThat(s.owners()).isEqualTo(1);
        assertThat(s.managers()).isEqualTo(2);
        assertThat(s.staff()).isEqualTo(1);
        assertThat(s.crossResto()).isEqualTo(1);   // u1 uniquement
    }
}
