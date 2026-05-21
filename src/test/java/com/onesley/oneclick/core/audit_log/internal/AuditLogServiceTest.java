package com.onesley.oneclick.core.audit_log.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.core.audit_log.api.AuditLogDtos.AuditLogCreateDto;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class AuditLogServiceTest {

    @Mock AuditLogRepository auditRepo;
    @Mock SystemEventRepository eventRepo;
    @Mock ErrorLogRepository errorRepo;
    @Mock JobExecutionRepository jobRepo;
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
