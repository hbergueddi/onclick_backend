package com.onesley.oneclick.modules.analytics;

import com.onesley.oneclick.core.tenant.Tenant;
import com.onesley.oneclick.exception.NotFoundException;
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

import static com.onesley.oneclick.modules.analytics.AnalyticsDtos.*;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final RestaurantSearchDocumentRepository searchDocRepo;
    private final ApiClientRepository apiClientRepo;
    private final ApiKeyRepository apiKeyRepo;
    private final WebhookRepository webhookRepo;
    private final WebhookDeliveryRepository deliveryRepo;

    @PersistenceContext
    private EntityManager entityManager;

    public AnalyticsService(RestaurantSearchDocumentRepository searchDocRepo,
                            ApiClientRepository apiClientRepo,
                            ApiKeyRepository apiKeyRepo,
                            WebhookRepository webhookRepo,
                            WebhookDeliveryRepository deliveryRepo) {
        this.searchDocRepo = searchDocRepo;
        this.apiClientRepo = apiClientRepo;
        this.apiKeyRepo = apiKeyRepo;
        this.webhookRepo = webhookRepo;
        this.deliveryRepo = deliveryRepo;
    }

    // ─── Search documents (read-only — gérés par trigger DB) ─────────────────

    public SearchDocDto findSearchDoc(UUID restaurantId) {
        return SearchDocDto.from(searchDocRepo.findById(restaurantId)
            .orElseThrow(() -> new NotFoundException("RestaurantSearchDocument", restaurantId)));
    }

    // ─── API clients ─────────────────────────────────────────────────────────

    public Page<ApiClientDto> findAllClients(UUID tenantId, int page, int size) {
        Specification<ApiClient> spec = (root, q, cb) -> cb.conjunction();
        if (tenantId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("tenantId"), tenantId));
        return apiClientRepo.findAll(spec, PageRequest.of(page, size, Sort.by("createdAt").descending()))
            .map(ApiClientDto::from);
    }

    public ApiClientDto findClientById(UUID id) {
        return ApiClientDto.from(apiClientRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("ApiClient", id)));
    }

    @Transactional
    public ApiClientDto createClient(ApiClientCreateDto dto) {
        ApiClient c = new ApiClient(UUID.randomUUID(), dto.name());
        if (dto.description() != null) c.setDescription(dto.description());
        if (dto.tenantId() != null) {
            c.setTenant(entityManager.getReference(Tenant.class, dto.tenantId()));
        }
        return ApiClientDto.from(apiClientRepo.save(c));
    }

    // ─── API keys ────────────────────────────────────────────────────────────

    public List<ApiKeyDto> findKeysByClient(UUID apiClientId) {
        return apiKeyRepo.findAllByApiClientId(apiClientId).stream().map(ApiKeyDto::from).toList();
    }

    @Transactional
    public ApiKeyDto createKey(ApiKeyCreateDto dto) {
        ApiClient clientRef = entityManager.getReference(ApiClient.class, dto.apiClientId());
        ApiKey k = new ApiKey(UUID.randomUUID(), clientRef, dto.keyHash(), dto.keyPrefix());
        if (dto.expiresAt() != null) k.setExpiresAt(dto.expiresAt());
        return ApiKeyDto.from(apiKeyRepo.save(k));
    }

    @Transactional
    public void revokeKey(UUID id) {
        ApiKey k = apiKeyRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("ApiKey", id));
        k.revoke();
        apiKeyRepo.save(k);
    }

    // ─── Webhooks ────────────────────────────────────────────────────────────

    public List<WebhookDto> findWebhooksByClient(UUID apiClientId) {
        return webhookRepo.findAllByApiClientId(apiClientId).stream().map(WebhookDto::from).toList();
    }

    @Transactional
    public WebhookDto createWebhook(WebhookCreateDto dto) {
        ApiClient clientRef = entityManager.getReference(ApiClient.class, dto.apiClientId());
        Webhook w = new Webhook(UUID.randomUUID(), clientRef, dto.url());
        if (dto.secret() != null) w.setSecret(dto.secret());
        return WebhookDto.from(webhookRepo.save(w));
    }

    @Transactional
    public void deleteWebhook(UUID id) {
        Webhook w = webhookRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Webhook", id));
        webhookRepo.delete(w);
    }

    // ─── Webhook deliveries (journal) ────────────────────────────────────────

    public Page<WebhookDeliveryDto> findDeliveriesByWebhook(UUID webhookId, int page, int size) {
        Specification<WebhookDelivery> spec = (root, q, cb) -> cb.equal(root.get("webhookId"), webhookId);
        return deliveryRepo.findAll(spec, PageRequest.of(page, size, Sort.by("createdAt").descending()))
            .map(WebhookDeliveryDto::from);
    }

    @Transactional
    public WebhookDeliveryDto recordDelivery(WebhookDeliveryCreateDto dto) {
        Webhook webhookRef = entityManager.getReference(Webhook.class, dto.webhookId());
        WebhookDelivery d = new WebhookDelivery(UUID.randomUUID(), webhookRef, dto.eventType(), dto.payload());
        return WebhookDeliveryDto.from(deliveryRepo.save(d));
    }
}
