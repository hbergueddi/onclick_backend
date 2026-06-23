package com.onesley.oneclick.core.tenant.internal;

import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.core.tenant.api.TenantCreateDto;
import com.onesley.oneclick.core.tenant.api.TenantAdminDtos.TenantBrandingDto;
import com.onesley.oneclick.core.tenant.api.TenantAdminDtos.TenantBrandingUpdateDto;
import com.onesley.oneclick.core.tenant.api.TenantAdminDtos.TenantFeatureDto;
import com.onesley.oneclick.core.tenant.api.TenantAdminDtos.TenantUpdateDto;
import com.onesley.oneclick.core.tenant.api.TenantDto;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock TenantRepository repository;
    @Mock TenantBrandingRepository brandingRepository;
    @Mock TenantFeatureRepository featureRepository;
    @Mock CompanySettingsRepository companySettingsRepository;
    @InjectMocks TenantService service;

    private Tenant tenant() { return new Tenant(UUID.randomUUID(), "OneClick", "oneclick"); }

    @Test
    void findAll_filtersDeleted() {
        Tenant deleted = tenant();
        ReflectionTestUtils.setField(deleted, "deletedAt", Instant.now());
        when(repository.findAll()).thenReturn(List.of(tenant(), deleted));
        assertThat(service.findAll()).hasSize(1);
    }

    @Test
    void findById_notFoundAndFound() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        Tenant t = tenant();
        when(repository.findById(t.getId())).thenReturn(Optional.of(t));
        when(companySettingsRepository.findByTenantId(t.getId())).thenReturn(Optional.empty());
        assertThat(service.findById(t.getId())).isNotNull();
    }

    @Test
    void findById_populatesLegalName_fromCompanySettings() {
        Tenant t = tenant();
        when(repository.findById(t.getId())).thenReturn(Optional.of(t));
        CompanySettings cs = new CompanySettings(UUID.randomUUID(), t, "OneClick SARL");
        when(companySettingsRepository.findByTenantId(t.getId())).thenReturn(Optional.of(cs));

        TenantDto dto = service.findById(t.getId());

        assertThat(dto.legalName()).isEqualTo("OneClick SARL");
    }

    @Test
    void findById_legalNameNull_whenNoCompanySettings() {
        Tenant t = tenant();
        when(repository.findById(t.getId())).thenReturn(Optional.of(t));
        when(companySettingsRepository.findByTenantId(t.getId())).thenReturn(Optional.empty());

        TenantDto dto = service.findById(t.getId());

        assertThat(dto.legalName()).isNull();
    }

    @Test
    void findAll_doesNotLookupCompanySettings_legalNameNull() {
        when(repository.findAll()).thenReturn(List.of(tenant()));
        List<TenantDto> out = service.findAll();
        assertThat(out).hasSize(1);
        assertThat(out.get(0).legalName()).isNull();
        // findAll ne doit JAMAIS toucher company_settings (pas de fuite légale + lookup léger).
        org.mockito.Mockito.verifyNoInteractions(companySettingsRepository);
    }

    @Test
    void findBySlug_doesNotLookupCompanySettings_legalNameNull() {
        when(repository.findBySlug("oneclick")).thenReturn(Optional.of(tenant()));
        TenantDto dto = service.findBySlug("oneclick");
        assertThat(dto.legalName()).isNull();
        // by-slug est PUBLIC → ne doit pas exposer la raison sociale.
        org.mockito.Mockito.verifyNoInteractions(companySettingsRepository);
    }

    // ─── Tenant.toDto overload (mapping entité → DTO) ────────────────────────────────

    @Test
    void tenantToDto_noArg_leavesLegalNameNull() {
        Tenant t = new Tenant(UUID.randomUUID(), "OneClick", "oneclick");
        assertThat(t.toDto().legalName()).isNull();
    }

    @Test
    void tenantToDto_withLegalName_setsIt() {
        Tenant t = new Tenant(UUID.randomUUID(), "OneClick", "oneclick");
        TenantDto dto = t.toDto("OneClick SARL");
        assertThat(dto.legalName()).isEqualTo("OneClick SARL");
        assertThat(dto.name()).isEqualTo("OneClick");
        assertThat(dto.slug()).isEqualTo("oneclick");
    }

    @Test
    void findBySlug_notFoundAndFound() {
        when(repository.findBySlug("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findBySlug("missing")).isInstanceOf(NotFoundException.class);
        when(repository.findBySlug("oneclick")).thenReturn(Optional.of(tenant()));
        assertThat(service.findBySlug("oneclick")).isNotNull();
    }

    @Test
    void create_conflict_whenSlugExists() {
        when(repository.findBySlug("oneclick")).thenReturn(Optional.of(tenant()));
        assertThatThrownBy(() -> service.create(new TenantCreateDto("OneClick", "oneclick")))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_success() {
        when(repository.findBySlug("homu")).thenReturn(Optional.empty());
        when(repository.save(any(Tenant.class))).thenAnswer(i -> i.getArgument(0));
        assertThat(service.create(new TenantCreateDto("HOMU", "homu"))).isNotNull();
    }

    // ─── C1 portail tenant-admin (SUPERADMIN-only) ───────────────────────────────

    private Tenant tenantOf(UUID id) { return new Tenant(id, "Acme", "acme"); }

    @Test
    void updateTenant_setsNameAndStatus() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(tenantOf(id)));
        when(repository.save(any(Tenant.class))).thenAnswer(i -> i.getArgument(0));
        TenantDto dto = service.updateTenant(id, new TenantUpdateDto("Acme Corp", "paused"));
        assertThat(dto.name()).isEqualTo("Acme Corp");
        assertThat(dto.status()).isEqualTo("paused");
    }

    @Test
    void updateTenant_invalidStatus_throws400() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(tenantOf(id)));
        assertThatThrownBy(() -> service.updateTenant(id, new TenantUpdateDto(null, "bogus")))
            .isInstanceOf(BadRequestException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void updateTenant_unknownTenant_throws404() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateTenant(id, new TenantUpdateDto("X", null)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getBranding_absent_returnsEmpty() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(tenantOf(id)));
        when(brandingRepository.findById(id)).thenReturn(Optional.empty());
        TenantBrandingDto dto = service.getBranding(id);
        assertThat(dto.tenantId()).isEqualTo(id);
        assertThat(dto.logoUrl()).isNull();
    }

    @Test
    void updateBranding_createsWhenAbsent() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(tenantOf(id)));
        when(brandingRepository.findById(id)).thenReturn(Optional.empty());
        when(brandingRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        TenantBrandingDto dto = service.updateBranding(id,
            new TenantBrandingUpdateDto("https://cdn/logo.png", "#714B67", "#28A745", null,
                "#FDFBF9", "https://cdn/logo-dark.png", "https://cdn/favicon.ico", "Le club",
                "My PCC", "PCC Store"));
        assertThat(dto.logoUrl()).isEqualTo("https://cdn/logo.png");
        assertThat(dto.primaryColor()).isEqualTo("#714B67");
        assertThat(dto.customDomain()).isNull();
        assertThat(dto.backgroundColor()).isEqualTo("#FDFBF9");
        assertThat(dto.tagline()).isEqualTo("Le club");
        assertThat(dto.appNameWin()).isEqualTo("My PCC");
        verify(brandingRepository).save(any(TenantBranding.class));
    }

    @Test
    void listFeatures_mapsRows() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(tenantOf(id)));
        when(featureRepository.findAllByTenantId(id)).thenReturn(List.of(
            new TenantFeature(UUID.randomUUID(), tenantOf(id), "theme_customization", true)));
        List<TenantFeatureDto> out = service.listFeatures(id);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).featureCode()).isEqualTo("theme_customization");
        assertThat(out.get(0).enabled()).isTrue();
    }

    @Test
    void toggleFeature_createsWhenAbsent() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(tenantOf(id)));
        when(featureRepository.findAllByTenantId(id)).thenReturn(List.of());
        when(featureRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        TenantFeatureDto dto = service.toggleFeature(id, "boutique", true);
        assertThat(dto.featureCode()).isEqualTo("boutique");
        assertThat(dto.enabled()).isTrue();
        verify(featureRepository).save(any(TenantFeature.class));
    }

    @Test
    void toggleFeature_updatesExisting() {
        UUID id = UUID.randomUUID();
        TenantFeature existing = new TenantFeature(UUID.randomUUID(), tenantOf(id), "boutique", true);
        when(repository.findById(id)).thenReturn(Optional.of(tenantOf(id)));
        when(featureRepository.findAllByTenantId(id)).thenReturn(List.of(existing));
        when(featureRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        TenantFeatureDto dto = service.toggleFeature(id, "boutique", false);
        assertThat(dto.enabled()).isFalse();
        assertThat(existing.isEnabled()).isFalse();
        verify(featureRepository).save(existing);
    }

    @Test
    void toggleFeature_blankCode_throws400() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(tenantOf(id)));
        assertThatThrownBy(() -> service.toggleFeature(id, "  ", true))
            .isInstanceOf(BadRequestException.class);
    }
}
