package com.onesley.oneclick.core.membership.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.core.membership.api.MembershipKpiDto;
import com.onesley.oneclick.exception.ForbiddenException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit isolé (Mockito) de {@link MembershipQueryService} — P3.
 * Couvre le mapping des KPIs (incl. member_type NULL) et l'ABAC own-tenant de la liste membres.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MembershipQueryServiceTest {

    @Mock MembershipRepository repository;
    @Mock UserDirectoryApi userDirectory;
    @InjectMocks MembershipQueryService service;

    private static final UUID PALMERAIE = UUID.fromString("0cccc000-0000-4000-8000-000000000001");
    private final UUID caller = UUID.randomUUID();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(UUID userId, String... authorities) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(userId.toString()).build();
        var granted = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).collect(Collectors.toList());
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, granted));
    }

    @Test
    void computeKpis_mapsCounts_andNullMemberTypeBucket() {
        when(repository.countActiveByTenant(PALMERAIE)).thenReturn(5L);
        when(repository.countActiveByTenantSince(org.mockito.ArgumentMatchers.eq(PALMERAIE),
                org.mockito.ArgumentMatchers.any())).thenReturn(2L);
        List<Object[]> grouped = new ArrayList<>();
        grouped.add(new Object[]{"titulaire", 3L});
        grouped.add(new Object[]{null, 2L}); // member_type NULL → bucket "(non précisé)"
        when(repository.countActiveByTenantGroupedByType(PALMERAIE)).thenReturn(grouped);

        MembershipKpiDto dto = service.computeKpis(PALMERAIE);

        assertThat(dto.tenantId()).isEqualTo(PALMERAIE);
        assertThat(dto.totalActive()).isEqualTo(5L);
        assertThat(dto.newLast7Days()).isEqualTo(2L);
        assertThat(dto.byType()).containsEntry("titulaire", 3L).containsEntry("(non précisé)", 2L);
    }

    @Test
    void listMembers_crossTenant_nonPlatformAdmin_throwsForbidden() {
        authenticate(caller, "VIEW:MEMBERSHIPS"); // pas DELETE:TENANTS
        when(userDirectory.tenantIdById(caller)).thenReturn(Optional.of(UUID.randomUUID())); // autre tenant
        assertThatThrownBy(() -> service.listMembers(PALMERAIE)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void listMembers_platformAdmin_enrichesNames() {
        authenticate(caller, "VIEW:MEMBERSHIPS", "DELETE:TENANTS"); // plateforme → bypass ABAC
        UUID memberId = UUID.randomUUID();
        TenantMembership m = org.mockito.Mockito.mock(TenantMembership.class);
        when(m.getUserId()).thenReturn(memberId);
        when(m.getMemberType()).thenReturn("titulaire");
        when(m.getStatus()).thenReturn(TenantMembership.STATUS_ACTIVE);
        when(repository.findAllByTenantIdAndStatusAndDeletedAtIsNull(PALMERAIE, TenantMembership.STATUS_ACTIVE))
                .thenReturn(List.of(m));
        when(userDirectory.namesByIds(List.of(memberId))).thenReturn(List.of(
                new UserDirectoryApi.UserName(memberId, "Sami", "Alaoui", "+212600", "sami@test.ma", null)));

        var members = service.listMembers(PALMERAIE);

        assertThat(members).hasSize(1);
        assertThat(members.get(0).firstName()).isEqualTo("Sami");
        assertThat(members.get(0).email()).isEqualTo("sami@test.ma");
        assertThat(members.get(0).memberType()).isEqualTo("titulaire");
    }
}
