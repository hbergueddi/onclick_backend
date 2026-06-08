package com.onesley.oneclick.core.membership.internal;

import com.onesley.oneclick.core.membership.api.MembershipDirectoryApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Tests unitaires isolés de {@link MembershipService} (repo mocké) — P0. */
@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {

    @Mock MembershipRepository repository;
    @InjectMocks MembershipService service;

    private final UUID user = UUID.randomUUID();
    private final UUID tenant = UUID.randomUUID();

    private TenantMembership membership(UUID tenantId, String memberType, String status) {
        return new TenantMembership(UUID.randomUUID(), user, tenantId, memberType, null, status, null, Instant.now());
    }

    @Test
    void isActiveMember_delegatesWithActiveStatus() {
        when(repository.existsByUserIdAndTenantIdAndStatusAndDeletedAtIsNull(user, tenant, TenantMembership.STATUS_ACTIVE))
                .thenReturn(true);
        assertThat(service.isActiveMember(user, tenant)).isTrue();
        verify(repository).existsByUserIdAndTenantIdAndStatusAndDeletedAtIsNull(user, tenant, "active");
    }

    @Test
    void isActiveMember_nullArgs_returnFalse_withoutHittingRepo() {
        assertThat(service.isActiveMember(null, tenant)).isFalse();
        assertThat(service.isActiveMember(user, null)).isFalse();
        verifyNoInteractions(repository);
    }

    @Test
    void activeMemberships_mapsEntitiesToProjection() {
        when(repository.findAllByUserIdAndStatusAndDeletedAtIsNull(user, TenantMembership.STATUS_ACTIVE))
                .thenReturn(List.of(membership(tenant, "resident", "active")));

        List<MembershipDirectoryApi.Membership> result = service.activeMemberships(user);

        assertThat(result).singleElement().satisfies(m -> {
            assertThat(m.tenantId()).isEqualTo(tenant);
            assertThat(m.memberType()).isEqualTo("resident");
            assertThat(m.status()).isEqualTo("active");
        });
    }

    @Test
    void activeTenantIds_extractsTenantIds() {
        UUID t2 = UUID.randomUUID();
        when(repository.findAllByUserIdAndStatusAndDeletedAtIsNull(user, TenantMembership.STATUS_ACTIVE))
                .thenReturn(List.of(membership(tenant, "resident", "active"), membership(t2, null, "active")));

        assertThat(service.activeTenantIds(user)).containsExactlyInAnyOrder(tenant, t2);
    }

    @Test
    void nullUser_returnsEmpty_withoutHittingRepo() {
        assertThat(service.activeMemberships(null)).isEmpty();
        assertThat(service.activeTenantIds(null)).isEmpty();
        verify(repository, never()).findAllByUserIdAndStatusAndDeletedAtIsNull(any(), eq("active"));
    }
}
