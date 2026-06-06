package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.restaurant.api.RestaurantCreateDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantPatchDto;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link RestaurantCatalogService} (L3 — modules.restaurant).
 * CRUD + filtres ville/tenant + PATCH partiel (tous champs / aucun champ).
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class RestaurantCatalogServiceTest {

    @Mock RestaurantRepository repository;
    @Mock EntityManager em;
    @Mock LifecycleEventService lifecycleEventService;
    @InjectMocks RestaurantCatalogService service;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "entityManager", em);
        lenient().when(em.getReference(eq(Tenant.class), any())).thenReturn(new Tenant(UUID.randomUUID(), "T", "t"));
        lenient().when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private Restaurant restaurant() {
        return new Restaurant(UUID.randomUUID(), new Tenant(UUID.randomUUID(), "T", "t"), "Resto", "Casablanca");
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAll_filtersAndNoFilters() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        assertThat(service.findAll("Casablanca", UUID.randomUUID(), 0, 20).getContent()).isEmpty();
        assertThat(service.findAll("  ", null, 0, 20).getContent()).isEmpty();
        assertThat(service.findAll(null, null, 0, 20).getContent()).isEmpty();
    }

    @Test
    void findById_notFoundAndFound() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        Restaurant r = restaurant();
        when(repository.findById(r.getId())).thenReturn(Optional.of(r));
        assertThat(service.findById(r.getId())).isNotNull();
    }

    @Test
    void create_full_andMinimal() {
        assertThat(service.create(new RestaurantCreateDto(UUID.randomUUID(), "Resto", "desc", "0600", "addr",
            "Casablanca", new BigDecimal("33.5"), new BigDecimal("-7.6"), "Marocaine", 10, UUID.randomUUID()))).isNotNull();
        assertThat(service.create(new RestaurantCreateDto(UUID.randomUUID(), "Simple", null, null, null,
            "Rabat", null, null, null, null, null))).isNotNull();
    }

    @Test
    void softDelete_notFoundAndSuccess() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.softDelete(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        Restaurant r = restaurant();
        when(repository.findById(r.getId())).thenReturn(Optional.of(r));
        service.softDelete(r.getId());
        assertThat(r.getDeletedAt()).isNotNull();
    }

    @Test
    void patch_notFound_throwsNotFound() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.patch(UUID.randomUUID(),
            new RestaurantPatchDto("X", null, null, null, null, null, null, null, null, null, null, null, null, null, null)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void patch_allFieldsApplied() {
        Restaurant r = restaurant();
        when(repository.findById(r.getId())).thenReturn(Optional.of(r));
        service.patch(r.getId(), new RestaurantPatchDto("Renommé", "desc", "0611", "addr2", "Rabat",
            new BigDecimal("34.0"), new BigDecimal("-6.8"), "active", "€€", List.of("tag1", "tag2"),
            50, "http://img", "Italienne", 8, UUID.randomUUID()));
        assertThat(r.getName()).isEqualTo("Renommé");
        assertThat(r.getCity()).isEqualTo("Rabat");
    }

    @Test
    void patch_noFields_noOp() {
        Restaurant r = restaurant();
        when(repository.findById(r.getId())).thenReturn(Optional.of(r));
        service.patch(r.getId(), new RestaurantPatchDto(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));
        assertThat(r.getName()).isEqualTo("Resto");
    }

    // ─── Producteurs lifecycle_events (V45) ──────────────────────────────────

    private RestaurantPatchDto patchStatus(String status) {
        return new RestaurantPatchDto(null, null, null, null, null, null, null, status, null, null, null, null, null, null, null);
    }

    @Test
    void create_journalisesInscription() {
        service.create(new RestaurantCreateDto(UUID.randomUUID(), "Resto", null, null, null,
            "Casablanca", null, null, null, null, null));
        verify(lifecycleEventService).record(eq("inscription"), any(UUID.class), anyString());
    }

    @Test
    void patch_statusActiveToPaused_journalisesSuspension() {
        Restaurant r = restaurant(); // statut défaut "active"
        when(repository.findById(r.getId())).thenReturn(Optional.of(r));
        service.patch(r.getId(), patchStatus("paused"));
        verify(lifecycleEventService).record(eq("suspension"), eq(r.getId()), anyString());
    }

    @Test
    void patch_statusPausedToActive_journalisesReactivation() {
        Restaurant r = restaurant();
        r.setStatus("paused");
        when(repository.findById(r.getId())).thenReturn(Optional.of(r));
        service.patch(r.getId(), patchStatus("active"));
        verify(lifecycleEventService).record(eq("réactivation"), eq(r.getId()), anyString());
    }

    @Test
    void patch_nonStatusFieldOnly_journalisesModification() {
        Restaurant r = restaurant();
        when(repository.findById(r.getId())).thenReturn(Optional.of(r));
        service.patch(r.getId(), new RestaurantPatchDto("Renommé", null, null, null, null, null, null, null, null, null, null, null, null, null, null));
        verify(lifecycleEventService).record(eq("modification"), eq(r.getId()), anyString());
    }

    @Test
    void patch_noFields_noLifecycleEvent() {
        Restaurant r = restaurant();
        when(repository.findById(r.getId())).thenReturn(Optional.of(r));
        service.patch(r.getId(), new RestaurantPatchDto(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));
        verify(lifecycleEventService, never()).record(any(), any(), any());
    }

    @Test
    void patch_statusUnchanged_noStatusEvent() {
        Restaurant r = restaurant(); // statut "active"
        when(repository.findById(r.getId())).thenReturn(Optional.of(r));
        // statut identique "active" sans autre champ → no-op, aucun événement
        service.patch(r.getId(), patchStatus("active"));
        verify(lifecycleEventService, never()).record(any(), any(), any());
    }

    // ─── E1 — markOnboardingComplete (V77) ───────────────────────────────────

    @Test
    void markOnboardingComplete_notFound_throwsNotFound() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.markOnboardingComplete(UUID.randomUUID()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void markOnboardingComplete_softDeleted_throwsNotFound() {
        Restaurant r = restaurant();
        r.markDeleted();
        when(repository.findById(r.getId())).thenReturn(Optional.of(r));
        assertThatThrownBy(() -> service.markOnboardingComplete(r.getId()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void markOnboardingComplete_success_stampsTimestamp() {
        Restaurant r = restaurant();
        assertThat(r.getOnboardingCompletedAt()).isNull();
        when(repository.findById(r.getId())).thenReturn(Optional.of(r));
        var dto = service.markOnboardingComplete(r.getId());
        assertThat(r.getOnboardingCompletedAt()).isNotNull();
        assertThat(dto.onboardingCompletedAt()).isEqualTo(r.getOnboardingCompletedAt());
        verify(repository).save(r);
    }
}
