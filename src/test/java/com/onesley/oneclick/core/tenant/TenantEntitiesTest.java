package com.onesley.oneclick.core.tenant;

import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.core.tenant.api.TenantDto;
import com.onesley.oneclick.core.tenant.internal.CompanySettings;
import com.onesley.oneclick.core.tenant.internal.TenantBranding;
import com.onesley.oneclick.core.tenant.internal.TenantFeature;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires des entités du module tenant (Layer 1). Purs (sans Spring).
 */
class TenantEntitiesTest {

    @Test
    void tenant_constructor_setters_toDto_equals() {
        UUID id = UUID.randomUUID();
        Tenant t = new Tenant(id, "OneClick", "oneclick");
        assertThat(t.getId()).isEqualTo(id);
        assertThat(t.getName()).isEqualTo("OneClick");
        assertThat(t.getSlug()).isEqualTo("oneclick");
        assertThat(t.getStatus()).isEqualTo("active"); // défaut

        t.setName("OneLabel"); t.setStatus("paused");
        TenantDto dto = t.toDto();
        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.name()).isEqualTo("OneLabel");
        assertThat(dto.slug()).isEqualTo("oneclick");
        assertThat(dto.status()).isEqualTo("paused");

        assertThat(t).isEqualTo(new Tenant(id, "x", "y"));               // equals par id
        assertThat(t).isNotEqualTo(new Tenant(UUID.randomUUID(), "OneClick", "oneclick"));
        assertThat(t).isNotEqualTo(null).isNotEqualTo("s");
        assertThat(t).hasSameHashCodeAs(new Tenant(id, "x", "y"));
    }

    @Test
    void tenantBranding_constructor_setters() {
        Tenant t = new Tenant(UUID.randomUUID(), "OneClick", "oneclick");
        TenantBranding b = new TenantBranding(t);
        b.setLogoUrl("http://logo"); b.setPrimaryColor("#714B67");
        b.setAccentColor("#28A745"); b.setCustomDomain("app.oneclick.ma");
        assertThat(b.getTenant()).isEqualTo(t);
        assertThat(b.getLogoUrl()).isEqualTo("http://logo");
        assertThat(b.getPrimaryColor()).isEqualTo("#714B67");
        assertThat(b.getAccentColor()).isEqualTo("#28A745");
        assertThat(b.getCustomDomain()).isEqualTo("app.oneclick.ma");
        assertThat(b).isEqualTo(b); // réflexif (tenantId null hors persistance)
    }

    @Test
    void tenantFeature_constructor_equals() {
        UUID id = UUID.randomUUID();
        Tenant t = new Tenant(UUID.randomUUID(), "OneClick", "oneclick");
        TenantFeature f = new TenantFeature(id, t, "PADEL", true);
        assertThat(f.getFeatureCode()).isEqualTo("PADEL");
        assertThat(f.isEnabled()).isTrue();
        assertThat(f.getTenant()).isEqualTo(t);
        assertThat(f).isEqualTo(new TenantFeature(id, t, "OTHER", false)); // id-based
        assertThat(f).isNotEqualTo(new TenantFeature(UUID.randomUUID(), t, "PADEL", true));
    }

    @Test
    void companySettings_constructor_equals() {
        UUID id = UUID.randomUUID();
        Tenant t = new Tenant(UUID.randomUUID(), "OneClick", "oneclick");
        CompanySettings cs = new CompanySettings(id, t, "Onesley SARL");
        assertThat(cs.getRaisonSociale()).isEqualTo("Onesley SARL");
        assertThat(cs.getTenant()).isEqualTo(t);
        assertThat(cs).isEqualTo(new CompanySettings(id, t, "Autre"));
        assertThat(cs).isNotEqualTo(new CompanySettings(UUID.randomUUID(), t, "Onesley SARL"));
    }
}
