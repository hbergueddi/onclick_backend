package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.modules.analytics.api.AnalyticsDtos.WebhookDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Webhook sortant — URL à appeler quand un event se produit. */
@Entity
@Table(name = "webhooks")
public class Webhook extends TimestampedEntity {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "api_client_id", nullable = false, insertable = false, updatable = false) private UUID apiClientId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "api_client_id", nullable = false) private ApiClient apiClient;
    @NotBlank @Column(name = "url", nullable = false) private String url;
    @Column(name = "secret") private String secret;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "event_types", columnDefinition = "jsonb") private List<String> eventTypes = new ArrayList<>();
    @Column(name = "enabled", nullable = false) private boolean enabled = true;

    protected Webhook() {}
    public Webhook(UUID id, ApiClient apiClient, String url) {
        this.id = id; this.apiClient = apiClient; this.url = url;
    }

    public UUID getId() { return id; }
    public UUID getApiClientId() { return apiClientId; }
    public ApiClient getApiClient() { return apiClient; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public List<String> getEventTypes() { return eventTypes; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** Mapping vers le DTO public exposé hors du module. */
    public WebhookDto toDto() {
        return new WebhookDto(id, apiClientId, url, eventTypes, enabled, getCreatedAt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && java.util.Objects.equals(id, ((Webhook) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
