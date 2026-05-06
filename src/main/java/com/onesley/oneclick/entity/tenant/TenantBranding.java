package com.onesley.oneclick.entity.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Entité {@code public.tenant_branding} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : entité simple.
 */
@Entity
@Table(name = "tenant_branding")
public class TenantBranding {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "logo_dark_url")
    private String logoDarkUrl;

    @Column(name = "favicon_url")
    private String faviconUrl;

    @Column(name = "primary_color")
    private String primaryColor;

    @Column(name = "accent_color")
    private String accentColor;

    @Column(name = "background_color")
    private String backgroundColor;

    @Column(name = "tagline")
    private String tagline;

    @Column(name = "app_name_win")
    private String appNameWin;

    @Column(name = "app_name_store")
    private String appNameStore;

    @Column(name = "custom_domain")
    private String customDomain;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TenantBranding() {
        // JPA
    }

    public UUID getTenantId() { return tenantId; }
    public String getLogoUrl() { return logoUrl; }
    public String getLogoDarkUrl() { return logoDarkUrl; }
    public String getFaviconUrl() { return faviconUrl; }
    public String getPrimaryColor() { return primaryColor; }
    public String getAccentColor() { return accentColor; }
    public String getBackgroundColor() { return backgroundColor; }
    public String getTagline() { return tagline; }
    public String getAppNameWin() { return appNameWin; }
    public String getAppNameStore() { return appNameStore; }
    public String getCustomDomain() { return customDomain; }
    public Instant getUpdatedAt() { return updatedAt; }
}
