package com.onesley.oneclick.modules.system.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.system.api.SystemDtos.AppDocumentUpsertDto;
import com.onesley.oneclick.modules.system.api.SystemDtos.CustomRoleCreateDto;
import com.onesley.oneclick.modules.system.api.SystemDtos.DocumentVersionCreateDto;
import com.onesley.oneclick.security.SecurityHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link SystemService} (L3 — modules.system).
 * Health checks, alertes, quota logs, documentation interne (upsert/versions),
 * rôles personnalisés. Test dans le package internal (entités package-protected).
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class SystemServiceTest {

    @Mock SystemHealthCheckRepository healthRepo;
    @Mock SystemAlertRepository alertRepo;
    @Mock SystemAlertRuleRepository ruleRepo;
    @Mock QuotaChangeLogRepository quotaRepo;
    @Mock AppDocumentRepository documentRepo;
    @Mock DocumentVersionRepository versionRepo;
    @Mock CustomRoleRepository customRoleRepo;
    @InjectMocks SystemService service;

    private AppDocument doc(String id) {
        AppDocument d = new AppDocument();
        d.setId(id); d.setContent("contenu"); d.setVersion("1");
        return d;
    }
    private CustomRole customRole() {
        CustomRole r = new CustomRole();
        r.setName("Role"); r.setPermissions(new String[0]);
        return r;
    }

    // ─── health / alerts / quota ────────────────────────────────────────────

    @Test
    void healthChecks_listingsAndRecord() {
        lenient().when(healthRepo.findRecent(any())).thenReturn(List.of(new SystemHealthCheck()));
        lenient().when(healthRepo.findByComponent(any(), any())).thenReturn(List.of(new SystemHealthCheck()));
        when(healthRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(service.findRecentHealthChecks(10)).hasSize(1);
        assertThat(service.findByComponent("db", 10)).hasSize(1);
        assertThat(service.recordHealthCheck(new SystemHealthCheck())).isNotNull();
    }

    @Test
    void alerts_listingsAndAcknowledge() {
        lenient().when(alertRepo.findUnacknowledged()).thenReturn(List.of(new SystemAlert()));
        lenient().when(alertRepo.findRecent(any())).thenReturn(List.of(new SystemAlert()));
        assertThat(service.findUnacknowledged()).hasSize(1);
        assertThat(service.findRecentAlerts(10)).hasSize(1);

        SystemAlert a = new SystemAlert();
        when(alertRepo.findById(any())).thenReturn(Optional.of(a));
        when(alertRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        service.acknowledge(UUID.randomUUID(), UUID.randomUUID());
        assertThat(a.getAcknowledgedAt()).isNotNull();
    }

    @Test
    void acknowledge_notFound_throwsNoSuchElement() {
        when(alertRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.acknowledge(UUID.randomUUID(), UUID.randomUUID()))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void rules_andQuotaLogs() {
        lenient().when(ruleRepo.findAllEnabled()).thenReturn(List.of(new SystemAlertRule()));
        lenient().when(quotaRepo.findRecent(any())).thenReturn(List.of(new QuotaChangeLog()));
        lenient().when(quotaRepo.findByRestaurant(any())).thenReturn(List.of(new QuotaChangeLog()));
        when(quotaRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(service.findRules()).hasSize(1);
        assertThat(service.findRecentQuotaLogs(10)).hasSize(1);
        assertThat(service.findQuotaLogsByRestaurant(UUID.randomUUID())).hasSize(1);
        assertThat(service.recordQuotaChange(new QuotaChangeLog())).isNotNull();
    }

    // ─── documents ────────────────────────────────────────────────────────────

    @Test
    void findDocument_notFoundAndSuccess() {
        when(documentRepo.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findDocument("missing")).isInstanceOf(NotFoundException.class);
        when(documentRepo.findById("doc1")).thenReturn(Optional.of(doc("doc1")));
        assertThat(service.findDocument("doc1")).isNotNull();
    }

    @Test
    void upsertDocument_createsWhenAbsent() {
        when(documentRepo.findById("doc1")).thenReturn(Optional.empty());
        when(documentRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThat(service.upsertDocument("doc1", new AppDocumentUpsertDto("nouveau contenu", "2"))).isNotNull();
        }
        verify(documentRepo).save(any(AppDocument.class));
    }

    @Test
    void upsertDocument_updatesExisting() {
        AppDocument d = doc("doc1");
        when(documentRepo.findById("doc1")).thenReturn(Optional.of(d));
        when(documentRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            service.upsertDocument("doc1", new AppDocumentUpsertDto("maj", "3"));
        }
        assertThat(d.getContent()).isEqualTo("maj");
        assertThat(d.getVersion()).isEqualTo("3");
    }

    @Test
    void findDocumentVersions_maps() {
        when(versionRepo.findByDocument("doc1")).thenReturn(List.of(new DocumentVersion()));
        assertThat(service.findDocumentVersions("doc1")).hasSize(1);
    }

    @Test
    void addDocumentVersion_notFoundAndSuccess() {
        when(documentRepo.existsById("missing")).thenReturn(false);
        assertThatThrownBy(() -> service.addDocumentVersion("missing",
            new DocumentVersionCreateDto("1", "c", "n"))).isInstanceOf(NotFoundException.class);

        when(documentRepo.existsById("doc1")).thenReturn(true);
        when(versionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(service.addDocumentVersion("doc1", new DocumentVersionCreateDto("2", "contenu", "notes"))).isNotNull();
    }

    // ─── custom roles ───────────────────────────────────────────────────────────

    @Test
    void findCustomRoles_maps() {
        when(customRoleRepo.findAllOrdered()).thenReturn(List.of(customRole()));
        assertThat(service.findCustomRoles()).hasSize(1);
    }

    @Test
    void createCustomRole_withPermissions_andNull() {
        when(customRoleRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThat(service.createCustomRole(new CustomRoleCreateDto("Manager", "desc", List.of("VIEW:X", "EDIT:X")))).isNotNull();
            assertThat(service.createCustomRole(new CustomRoleCreateDto("Empty", null, null))).isNotNull();
        }
    }

    @Test
    void deleteCustomRole_notFoundAndSuccess() {
        when(customRoleRepo.existsById(any())).thenReturn(false);
        assertThatThrownBy(() -> service.deleteCustomRole(UUID.randomUUID())).isInstanceOf(NotFoundException.class);

        UUID id = UUID.randomUUID();
        when(customRoleRepo.existsById(id)).thenReturn(true);
        service.deleteCustomRole(id);
        verify(customRoleRepo).deleteById(id);
    }
}
