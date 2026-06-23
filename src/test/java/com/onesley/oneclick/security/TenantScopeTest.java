package com.onesley.oneclick.security;

import com.onesley.oneclick.core.membership.api.MembershipDirectoryApi;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires isolés du périmètre {@link TenantScope#publicCatalogScopeOrNull()} (fix fuite
 * « Coiffeur PCC dans Explore ») : un client (membre ou non) → tenant public « oneclick » seul ;
 * un acteur cross-tenant (SUPERADMIN, {@code VIEW:TENANTS}) → {@code null} (vue globale) ; tenant
 * public introuvable → ensemble vide (fail-closed). {@code SecurityHelper} (statique) mocké.
 */
class TenantScopeTest {

    private static final UUID PUBLIC_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final MembershipDirectoryApi membership = mock(MembershipDirectoryApi.class);
    private final TenantScope scope = new TenantScope(membership);

    @Test
    void publicCatalogScope_client_returnsPublicTenantOnly() {
        when(membership.publicTenantId()).thenReturn(Optional.of(PUBLIC_TENANT));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(() -> SecurityHelper.hasAuthority("VIEW:TENANTS")).thenReturn(false);

            assertThat(scope.publicCatalogScopeOrNull()).containsExactly(PUBLIC_TENANT);
        }
    }

    @Test
    void publicCatalogScope_superadmin_returnsNullNoFilter() {
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(() -> SecurityHelper.hasAuthority("VIEW:TENANTS")).thenReturn(true);

            assertThat(scope.publicCatalogScopeOrNull()).isNull();
        }
    }

    @Test
    void publicCatalogScope_publicTenantMissing_failsClosedEmpty() {
        when(membership.publicTenantId()).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(() -> SecurityHelper.hasAuthority("VIEW:TENANTS")).thenReturn(false);

            // Fail-closed : tenant public introuvable → ensemble vide (catalogue vide), pas null.
            assertThat(scope.publicCatalogScopeOrNull()).isNotNull().isEmpty();
        }
    }
}
