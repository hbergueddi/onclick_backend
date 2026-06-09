package com.onesley.oneclick.core.tenant.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.core.identity.api.UserDirectoryApi.UserName;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.core.tenant.api.TenantAdminDtos.AddTenantAdminDto;
import com.onesley.oneclick.core.tenant.api.TenantAdminDtos.TenantAdminDto;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.SecurityHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link TenantAdminService} (portail tenant-admin C2, SUPERADMIN-only).
 *
 * <p>Couvre : list (enrichi noms), add (résolution UserDirectory + save, user introuvable → 404,
 * doublon → 409), remove (404 si non-admin), tenant inconnu → 404.</p>
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class TenantAdminServiceTest {

    @Mock TenantRepository tenantRepository;
    @Mock TenantAdminRepository repo;
    @Mock UserDirectoryApi userDirectory;
    @InjectMocks TenantAdminService service;

    private MockedStatic<SecurityHelper> securityMock;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID caller = UUID.randomUUID();

    @BeforeEach
    void setup() {
        securityMock = mockStaticSecurity();
        lenient().when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(new Tenant(tenantId, "Acme", "acme")));
        lenient().when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private MockedStatic<SecurityHelper> mockStaticSecurity() {
        MockedStatic<SecurityHelper> m = org.mockito.Mockito.mockStatic(SecurityHelper.class);
        m.when(SecurityHelper::currentUserId).thenReturn(caller);
        return m;
    }

    @AfterEach
    void tearDown() {
        if (securityMock != null) securityMock.close();
    }

    private UserName user(UUID id) {
        return new UserName(id, "Karim", "Benali", "+212600", "karim@a.ma", null);
    }

    // ─── list ─────────────────────────────────────────────────────────────────────

    @Test
    void listAdmins_enrichesNames() {
        UUID userId = UUID.randomUUID();
        TenantAdmin row = new TenantAdmin(UUID.randomUUID(), tenantId, userId, "admin", caller);
        when(repo.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(row));
        when(userDirectory.namesByIds(List.of(userId))).thenReturn(List.of(user(userId)));

        List<TenantAdminDto> out = service.listAdmins(tenantId);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).userId()).isEqualTo(userId);
        assertThat(out.get(0).email()).isEqualTo("karim@a.ma");
        assertThat(out.get(0).role()).isEqualTo("admin");
    }

    @Test
    void listAdmins_unknownTenant_throws404() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.listAdmins(tenantId)).isInstanceOf(NotFoundException.class);
    }

    // ─── add ──────────────────────────────────────────────────────────────────────

    @Test
    void addAdmin_resolvesAndSaves() {
        UUID userId = UUID.randomUUID();
        when(userDirectory.findByIdentifier("karim@a.ma")).thenReturn(Optional.of(user(userId)));
        when(repo.existsByTenantIdAndUserId(tenantId, userId)).thenReturn(false);

        TenantAdminDto dto = service.addAdmin(tenantId, new AddTenantAdminDto("karim@a.ma", "owner"));

        assertThat(dto.userId()).isEqualTo(userId);
        assertThat(dto.role()).isEqualTo("owner");
        verify(repo).save(any(TenantAdmin.class));
    }

    @Test
    void addAdmin_userNotFound_throws404() {
        when(userDirectory.findByIdentifier("ghost@a.ma")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addAdmin(tenantId, new AddTenantAdminDto("ghost@a.ma", null)))
            .isInstanceOf(NotFoundException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void addAdmin_duplicate_throws409() {
        UUID userId = UUID.randomUUID();
        when(userDirectory.findByIdentifier("karim@a.ma")).thenReturn(Optional.of(user(userId)));
        when(repo.existsByTenantIdAndUserId(tenantId, userId)).thenReturn(true);
        assertThatThrownBy(() -> service.addAdmin(tenantId, new AddTenantAdminDto("karim@a.ma", "admin")))
            .isInstanceOf(ConflictException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void addAdmin_defaultsRoleToAdmin() {
        UUID userId = UUID.randomUUID();
        when(userDirectory.findByIdentifier("karim@a.ma")).thenReturn(Optional.of(user(userId)));
        when(repo.existsByTenantIdAndUserId(tenantId, userId)).thenReturn(false);
        TenantAdminDto dto = service.addAdmin(tenantId, new AddTenantAdminDto("karim@a.ma", null));
        assertThat(dto.role()).isEqualTo("admin");
    }

    // ─── remove ─────────────────────────────────────────────────────────────────

    @Test
    void removeAdmin_notFound_throws404() {
        UUID userId = UUID.randomUUID();
        when(repo.findByTenantIdAndUserId(tenantId, userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.removeAdmin(tenantId, userId)).isInstanceOf(NotFoundException.class);
        verify(repo, never()).delete(any());
    }

    @Test
    void removeAdmin_deletesRow() {
        UUID userId = UUID.randomUUID();
        TenantAdmin row = new TenantAdmin(UUID.randomUUID(), tenantId, userId, "admin", caller);
        when(repo.findByTenantIdAndUserId(tenantId, userId)).thenReturn(Optional.of(row));
        service.removeAdmin(tenantId, userId);
        verify(repo).delete(row);
    }
}
