package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.analytics.api.AnalyticsDtos.ApiClientCreateDto;
import com.onesley.oneclick.modules.analytics.api.AnalyticsDtos.ApiKeyCreateDto;
import com.onesley.oneclick.modules.analytics.api.AnalyticsDtos.WebhookCreateDto;
import com.onesley.oneclick.modules.analytics.api.AnalyticsDtos.WebhookDeliveryCreateDto;
import com.onesley.oneclick.security.SecurityHelper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link AnalyticsService} (L3 — modules.analytics).
 * API clients / keys / webhooks / deliveries CRUD + RBAC via SecurityHelper mocké.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsServiceTest {

    @Mock ApiClientRepository apiClientRepo;
    @Mock ApiKeyRepository apiKeyRepo;
    @Mock WebhookRepository webhookRepo;
    @Mock WebhookDeliveryRepository deliveryRepo;
    @Mock EntityManager em;
    @InjectMocks AnalyticsService service;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "entityManager", em);
        lenient().when(em.getReference(eq(Tenant.class), any())).thenReturn(new Tenant(UUID.randomUUID(), "T", "t"));
        lenient().when(em.getReference(eq(ApiClient.class), any())).thenReturn(client());
        lenient().when(em.getReference(eq(Webhook.class), any())).thenReturn(webhook());
        lenient().when(apiClientRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(apiKeyRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(webhookRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(deliveryRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private ApiClient client() { return new ApiClient(UUID.randomUUID(), "Client"); }
    private ApiKey key() { return new ApiKey(UUID.randomUUID(), client(), "hash", "pref_"); }
    private Webhook webhook() { return new Webhook(UUID.randomUUID(), client(), "https://x/hook"); }

    // ─── API clients ─────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void findAllClients_filterAndNoFilter() {
        when(apiClientRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        assertThat(service.findAllClients(UUID.randomUUID(), 0, 20).getContent()).isEmpty();
        assertThat(service.findAllClients(null, 0, 20).getContent()).isEmpty();
    }

    @Test
    void findClientById_notFoundAndFound() {
        when(apiClientRepo.findById(any())).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThatThrownBy(() -> service.findClientById(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        }
        ApiClient c = client();
        when(apiClientRepo.findById(c.getId())).thenReturn(Optional.of(c));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThat(service.findClientById(c.getId())).isNotNull();
        }
    }

    @Test
    void createClient_fullAndMinimal() {
        assertThat(service.createClient(new ApiClientCreateDto("C", "desc", UUID.randomUUID()))).isNotNull();
        assertThat(service.createClient(new ApiClientCreateDto("C2", null, null))).isNotNull();
    }

    // ─── API keys ──────────────────────────────────────────────────────────────

    @Test
    void findKeysByClient_notFoundAndFound() {
        when(apiClientRepo.findById(any())).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThatThrownBy(() -> service.findKeysByClient(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        }
        ApiClient c = client();
        when(apiClientRepo.findById(c.getId())).thenReturn(Optional.of(c));
        when(apiKeyRepo.findAllByApiClientId(c.getId())).thenReturn(List.of(key()));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThat(service.findKeysByClient(c.getId())).hasSize(1);
        }
    }

    @Test
    void createKey_fullAndMinimal() {
        assertThat(service.createKey(new ApiKeyCreateDto(UUID.randomUUID(), "hash", "pref_", Instant.now()))).isNotNull();
        assertThat(service.createKey(new ApiKeyCreateDto(UUID.randomUUID(), "hash", "pref_", null))).isNotNull();
    }

    @Test
    void revokeKey_keyNotFound_clientNotFound_success() {
        when(apiKeyRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.revokeKey(UUID.randomUUID())).isInstanceOf(NotFoundException.class);

        ApiKey k = key();
        when(apiKeyRepo.findById(k.getId())).thenReturn(Optional.of(k));
        when(apiClientRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.revokeKey(k.getId())).isInstanceOf(NotFoundException.class);

        when(apiClientRepo.findById(any())).thenReturn(Optional.of(client()));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            service.revokeKey(k.getId());
            assertThat(k.getRevokedAt()).isNotNull();
        }
    }

    // ─── Webhooks ──────────────────────────────────────────────────────────────

    @Test
    void findWebhooksByClient_notFoundAndFound() {
        when(apiClientRepo.findById(any())).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThatThrownBy(() -> service.findWebhooksByClient(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        }
        ApiClient c = client();
        when(apiClientRepo.findById(c.getId())).thenReturn(Optional.of(c));
        when(webhookRepo.findAllByApiClientId(c.getId())).thenReturn(List.of(webhook()));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThat(service.findWebhooksByClient(c.getId())).hasSize(1);
        }
    }

    @Test
    void createWebhook_fullAndMinimal() {
        assertThat(service.createWebhook(new WebhookCreateDto(UUID.randomUUID(), "https://x/h", "secret"))).isNotNull();
        assertThat(service.createWebhook(new WebhookCreateDto(UUID.randomUUID(), "https://x/h", null))).isNotNull();
    }

    @Test
    void deleteWebhook_notFound_clientNotFound_success() {
        when(webhookRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteWebhook(UUID.randomUUID())).isInstanceOf(NotFoundException.class);

        Webhook w = webhook();
        when(webhookRepo.findById(w.getId())).thenReturn(Optional.of(w));
        when(apiClientRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteWebhook(w.getId())).isInstanceOf(NotFoundException.class);

        when(apiClientRepo.findById(any())).thenReturn(Optional.of(client()));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            service.deleteWebhook(w.getId());
        }
    }

    // ─── Deliveries ──────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void findDeliveriesByWebhook_notFound_clientNotFound_success() {
        when(webhookRepo.findById(any())).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThatThrownBy(() -> service.findDeliveriesByWebhook(UUID.randomUUID(), 0, 20)).isInstanceOf(NotFoundException.class);
        }
        Webhook w = webhook();
        when(webhookRepo.findById(w.getId())).thenReturn(Optional.of(w));
        when(apiClientRepo.findById(any())).thenReturn(Optional.of(client()));
        when(deliveryRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThat(service.findDeliveriesByWebhook(w.getId(), 0, 20).getContent()).isEmpty();
        }
    }

    @Test
    void recordDelivery_creates() {
        assertThat(service.recordDelivery(new WebhookDeliveryCreateDto(UUID.randomUUID(), "resa.created", Map.of("k", "v")))).isNotNull();
    }
}
