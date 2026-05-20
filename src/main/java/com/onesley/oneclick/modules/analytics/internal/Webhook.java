package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.modules.analytics.api.AnalyticsDtos.WebhookDto;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Webhook sortant — URL à appeler quand un event se produit. */
@Entity
@Table(name = "webhooks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Webhook extends TimestampedEntity {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "api_client_id", nullable = false, insertable = false, updatable = false) private UUID apiClientId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "api_client_id", nullable = false) private ApiClient apiClient;
     @Column(name = "url", nullable = false, length = 512) @Setter private String url;
    @Column(name = "secret", length = 64) @Setter private String secret;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "event_types", columnDefinition = "jsonb") private List<String> eventTypes = new ArrayList<>();
    @Column(name = "enabled", nullable = false) @Setter private boolean enabled = true;
    public Webhook(UUID id, ApiClient apiClient, String url) {
        this.id = id; this.apiClient = apiClient; this.url = url;
    }

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
