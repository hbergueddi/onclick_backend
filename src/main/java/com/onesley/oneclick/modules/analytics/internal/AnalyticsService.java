package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.SecurityHelper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.onesley.oneclick.modules.analytics.api.AnalyticsDtos.*;
import com.onesley.oneclick.modules.analytics.api.AnalyticsDtos;
import com.onesley.oneclick.modules.analytics.api.AnalyticsDtos.ApiClientCreateDto;
import com.onesley.oneclick.modules.analytics.api.AnalyticsDtos.ApiClientDto;
import com.onesley.oneclick.modules.analytics.api.AnalyticsDtos.ApiKeyCreateDto;
import com.onesley.oneclick.modules.analytics.api.AnalyticsDtos.ApiKeyDto;
import com.onesley.oneclick.modules.analytics.api.AnalyticsDtos.WebhookCreateDto;
import com.onesley.oneclick.modules.analytics.api.AnalyticsDtos.WebhookDeliveryCreateDto;
import com.onesley.oneclick.modules.analytics.api.AnalyticsDtos.WebhookDeliveryDto;
import com.onesley.oneclick.modules.analytics.api.AnalyticsDtos.WebhookDto;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {
    private final ApiClientRepository apiClientRepo;
    private final ApiKeyRepository apiKeyRepo;
    private final WebhookRepository webhookRepo;
    private final WebhookDeliveryRepository deliveryRepo;

    @PersistenceContext
    private EntityManager entityManager;

    public AnalyticsService(ApiClientRepository apiClientRepo,
                            ApiKeyRepository apiKeyRepo,
                            WebhookRepository webhookRepo,
                            WebhookDeliveryRepository deliveryRepo) {
        this.apiClientRepo = apiClientRepo;
        this.apiKeyRepo = apiKeyRepo;
        this.webhookRepo = webhookRepo;
        this.deliveryRepo = deliveryRepo;
    }
    

    // ─── API clients ─────────────────────────────────────────────────────────

    public Page<ApiClientDto> findAllClients(UUID tenantId, int page, int size) {
        Specification<ApiClient> spec = (root, q, cb) -> cb.conjunction();
        if (tenantId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("tenantId"), tenantId));
        return apiClientRepo.findAll(spec, PageRequest.of(page, size, Sort.by("createdAt").descending()))
            .map(ApiClient::toDto);
    }

    public ApiClientDto findClientById(UUID id) {
        ApiClient c = apiClientRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("ApiClient", id));
        // TODO check ownership — ApiClient n'a pas de createdBy direct, fallback tenantId
        // (effectivement admin-only car tenantId != userId courant)
        SecurityHelper.requireOwnerOrAdmin(c.getTenantId());
        return c.toDto();
    }

    @Transactional
    public ApiClientDto createClient(ApiClientCreateDto dto) {
        ApiClient c = new ApiClient(UUID.randomUUID(), dto.name());
        if (dto.description() != null) c.setDescription(dto.description());
        if (dto.tenantId() != null) {
            c.setTenant(entityManager.getReference(Tenant.class, dto.tenantId()));
        }
        return apiClientRepo.save(c).toDto();
    }

    // ─── API keys ────────────────────────────────────────────────────────────

    public List<ApiKeyDto> findKeysByClient(UUID apiClientId) {
        ApiClient c = apiClientRepo.findById(apiClientId)
            .orElseThrow(() -> new NotFoundException("ApiClient", apiClientId));
        // TODO check ownership — ApiClient n'a pas de createdBy direct, fallback tenantId
        SecurityHelper.requireOwnerOrAdmin(c.getTenantId());
        return apiKeyRepo.findAllByApiClientId(apiClientId).stream().map(ApiKey::toDto).toList();
    }

    @Transactional
    public ApiKeyDto createKey(ApiKeyCreateDto dto) {
        ApiClient clientRef = entityManager.getReference(ApiClient.class, dto.apiClientId());
        ApiKey k = new ApiKey(UUID.randomUUID(), clientRef, dto.keyHash(), dto.keyPrefix());
        if (dto.expiresAt() != null) k.setExpiresAt(dto.expiresAt());
        return apiKeyRepo.save(k).toDto();
    }

    @Transactional
    public void revokeKey(UUID id) {
        ApiKey k = apiKeyRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("ApiKey", id));
        // Check via le parent ApiClient (TODO: ApiClient n'a pas de createdBy direct)
        ApiClient parent = apiClientRepo.findById(k.getApiClientId())
            .orElseThrow(() -> new NotFoundException("ApiClient", k.getApiClientId()));
        SecurityHelper.requireOwnerOrAdmin(parent.getTenantId());
        k.revoke();
        apiKeyRepo.save(k);
    }

    // ─── Webhooks ────────────────────────────────────────────────────────────

    public List<WebhookDto> findWebhooksByClient(UUID apiClientId) {
        ApiClient c = apiClientRepo.findById(apiClientId)
            .orElseThrow(() -> new NotFoundException("ApiClient", apiClientId));
        // TODO check ownership — ApiClient n'a pas de createdBy direct, fallback tenantId
        SecurityHelper.requireOwnerOrAdmin(c.getTenantId());
        return webhookRepo.findAllByApiClientId(apiClientId).stream().map(Webhook::toDto).toList();
    }

    @Transactional
    public WebhookDto createWebhook(WebhookCreateDto dto) {
        ApiClient clientRef = entityManager.getReference(ApiClient.class, dto.apiClientId());
        Webhook w = new Webhook(UUID.randomUUID(), clientRef, dto.url());
        if (dto.secret() != null) w.setSecret(dto.secret());
        return webhookRepo.save(w).toDto();
    }

    @Transactional
    public void deleteWebhook(UUID id) {
        Webhook w = webhookRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Webhook", id));
        // Check via le parent ApiClient (TODO: ApiClient n'a pas de createdBy direct)
        ApiClient parent = apiClientRepo.findById(w.getApiClientId())
            .orElseThrow(() -> new NotFoundException("ApiClient", w.getApiClientId()));
        SecurityHelper.requireOwnerOrAdmin(parent.getTenantId());
        webhookRepo.delete(w);
    }

    // ─── Webhook deliveries (journal) ────────────────────────────────────────

    public Page<WebhookDeliveryDto> findDeliveriesByWebhook(UUID webhookId, int page, int size) {
        Webhook w = webhookRepo.findById(webhookId)
            .orElseThrow(() -> new NotFoundException("Webhook", webhookId));
        // Check via le parent ApiClient (TODO: ApiClient n'a pas de createdBy direct)
        ApiClient parent = apiClientRepo.findById(w.getApiClientId())
            .orElseThrow(() -> new NotFoundException("ApiClient", w.getApiClientId()));
        SecurityHelper.requireOwnerOrAdmin(parent.getTenantId());
        Specification<WebhookDelivery> spec = (root, q, cb) -> cb.equal(root.get("webhookId"), webhookId);
        return deliveryRepo.findAll(spec, PageRequest.of(page, size, Sort.by("createdAt").descending()))
            .map(WebhookDelivery::toDto);
    }

    @Transactional
    public WebhookDeliveryDto recordDelivery(WebhookDeliveryCreateDto dto) {
        Webhook webhookRef = entityManager.getReference(Webhook.class, dto.webhookId());
        WebhookDelivery d = new WebhookDelivery(UUID.randomUUID(), webhookRef, dto.eventType(), dto.payload());
        return deliveryRepo.save(d).toDto();
    }
}
