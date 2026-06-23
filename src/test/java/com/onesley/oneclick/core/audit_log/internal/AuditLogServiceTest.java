package com.onesley.oneclick.core.audit_log.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.core.identity.api.UserDirectoryApi.UserName;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.core.audit_log.api.AuditLogDtos.AuditLogCreateDto;
import com.onesley.oneclick.core.audit_log.api.AuditLogDtos.AuditLogDto;
import com.onesley.oneclick.core.audit_log.api.AuditLogDtos.ErrorLogCreateDto;
import com.onesley.oneclick.core.audit_log.api.AuditLogDtos.SystemEventCreateDto;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class AuditLogServiceTest {

    @Mock AuditLogRepository auditRepo;
    @Mock SystemEventRepository eventRepo;
    @Mock ErrorLogRepository errorRepo;
    @Mock JobExecutionRepository jobRepo;
    @Mock UserDirectoryApi userDirectory;
    @Mock EntityManager em;
    @InjectMocks AuditLogService service;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "entityManager", em);
        lenient().when(em.getReference(eq(User.class), any())).thenReturn(new User(UUID.randomUUID(), null, "u@x.ma", "h", "U", "U"));
        lenient().when(em.getReference(eq(Tenant.class), any())).thenReturn(new Tenant(UUID.randomUUID(), "T", "t"));
        lenient().when(auditRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(eventRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(errorRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void finders_delegate() {
        when(auditRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        when(eventRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        when(errorRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        when(jobRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        assertThat(service.findAuditLogs(UUID.randomUUID(), UUID.randomUUID(), "User", UUID.randomUUID(), 0, 20).getContent()).isEmpty();
        assertThat(service.findEvents("type", true, 0, 20).getContent()).isEmpty();
        assertThat(service.findErrors("svc", "error", 0, 20).getContent()).isEmpty();
        assertThat(service.findJobs("job", "ok", 0, 20).getContent()).isEmpty();
    }

    @Test
    void findAuditLogs_enrichesUserNameInBatch() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        AuditLog withUser = auditLog(u1);
        AuditLog system = auditLog(null);          // action système (userId null)
        AuditLog deletedUser = auditLog(u2);        // user supprimé → absent de namesByIds
        when(auditRepo.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(withUser, system, deletedUser)));
        when(userDirectory.namesByIds(anyList()))
            .thenReturn(List.of(new UserName(u1, "Sara", "Kabbaj", null, null, null)));

        List<AuditLogDto> out = service.findAuditLogs(null, null, null, null, 0, 50).getContent();

        assertThat(out).hasSize(3);
        assertThat(out.get(0).userName()).isEqualTo("Sara Kabbaj"); // résolu
        assertThat(out.get(1).userName()).isNull();                  // système (userId null)
        assertThat(out.get(2).userName()).isNull();                  // user introuvable/supprimé
    }

    @Test
    void findAuditLogs_skipsDirectoryWhenNoUsers() {
        AuditLog system = auditLog(null);
        when(auditRepo.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(system)));

        service.findAuditLogs(null, null, null, null, 0, 50);

        // Aucune ligne avec userId → pas d'appel annuaire (anti-N+1 / pas de requête inutile).
        verify(userDirectory, never()).namesByIds(anyList());
    }

    @Test
    void findAuditLogs_dedupesUserIdsForSinglePageQuery() {
        UUID u1 = UUID.randomUUID();
        when(auditRepo.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(auditLog(u1), auditLog(u1), auditLog(u1))));
        when(userDirectory.namesByIds(anyList()))
            .thenReturn(List.of(new UserName(u1, "Karim", "Benali", null, null, null)));

        List<AuditLogDto> out = service.findAuditLogs(null, null, null, null, 0, 50).getContent();

        assertThat(out).hasSize(3);
        out.forEach(d -> assertThat(d.userName()).isEqualTo("Karim Benali"));
        // 1 seule requête annuaire pour les 3 lignes (mêmes 3 userId dédupliqués → 1 id).
        verify(userDirectory).namesByIds(List.of(u1));
    }

    /** Construit un AuditLog avec un userId connu (champ read-only → reflection). */
    private static AuditLog auditLog(UUID userId) {
        AuditLog a = new AuditLog(UUID.randomUUID(), null, "reservation", UUID.randomUUID(), "approved");
        if (userId != null) {
            ReflectionTestUtils.setField(a, "userId", userId);
        }
        return a;
    }

    @Test
    void recordAudit_full_andMinimal() {
        assertThat(service.recordAudit(new AuditLogCreateDto(UUID.randomUUID(), UUID.randomUUID(), "User",
            UUID.randomUUID(), "UPDATE", Map.of("k", "v"), "1.2.3.4", "agent"))).isNotNull();
        assertThat(service.recordAudit(new AuditLogCreateDto(null, null, "User", UUID.randomUUID(), "VIEW", null, null, null))).isNotNull();
    }

    @Test
    void publishEvent_withAndWithoutPayload() {
        assertThat(service.publishEvent(new SystemEventCreateDto("restaurant.updated", Map.of("id", "x")))).isNotNull();
        assertThat(service.publishEvent(new SystemEventCreateDto("ping", Map.of()))).isNotNull();
    }

    @Test
    void recordError_withSeverityAndWithoutStacktrace() {
        assertThat(service.recordError(new ErrorLogCreateDto("frontend", "NPE", "stack...", "fatal"))).isNotNull();
        assertThat(service.recordError(new ErrorLogCreateDto("frontend", "warn", null, null))).isNotNull();
    }
}
