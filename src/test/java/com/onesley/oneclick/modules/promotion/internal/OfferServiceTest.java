package com.onesley.oneclick.modules.promotion.internal;

import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.promotion.api.OfferCreateDto;
import com.onesley.oneclick.modules.promotion.api.OfferDto;
import com.onesley.oneclick.modules.promotion.api.OfferPatchDto;
import com.onesley.oneclick.search.SearchRequest;
import com.onesley.oneclick.shared.events.OfferCreatedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class OfferServiceTest {

    @Mock OfferRepository repository;
    @Mock OfferImpressionRepository impressionRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock com.onesley.oneclick.security.TenantScope tenantScope;
    @Mock EntityManager em;
    @Mock Query query;
    @InjectMocks OfferService service;

    private final Instant start = Instant.now();
    private final Instant end = start.plusSeconds(86400);

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "entityManager", em);
        lenient().when(em.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(query.setParameter(anyInt(), any())).thenReturn(query);
        lenient().when(query.getSingleResult()).thenReturn(UUID.randomUUID());
        lenient().when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        // Défaut tests : aucun filtre tenant (équiv. SUPERADMIN) → comportement legacy non scopé.
        lenient().when(tenantScope.visibleTenantIdsOrNull()).thenReturn(null);
        lenient().when(tenantScope.canSeeTenant(any())).thenReturn(true);
    }

    private Offer offer() {
        return new Offer(UUID.randomUUID(), UUID.randomUUID(), "Promo", start, end);
    }
    private OfferCreateDto createDto(Instant s, Instant e, String type, Integer pts) {
        return new OfferCreateDto(UUID.randomUUID(), "Promo", "desc", s, e, null, null, type, pts, true, "img", null, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAll_delegates_activeOnly() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        assertThat(service.findAll(UUID.randomUUID(), true, 0, 20).getContent()).isEmpty();
    }

    @Test
    void findById_notFoundAndFound() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        Offer o = offer();
        when(repository.findById(o.getId())).thenReturn(Optional.of(o));
        assertThat(service.findById(o.getId())).isNotNull();
    }

    @Test
    void create_expiresBeforeStarts_throwsBadRequest() {
        assertThatThrownBy(() -> service.create(createDto(end, start, "promo", null)))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_invalidType_throwsBadRequest() {
        assertThatThrownBy(() -> service.create(createDto(start, end, "bogus", null)))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_invalidPts_throwsBadRequest() {
        assertThatThrownBy(() -> service.create(createDto(start, end, "bonus", -5)))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_success_publishesEvent() {
        assertThat(service.create(createDto(start, end, null, 10))).isNotNull(); // type null -> "promo"
        verify(eventPublisher).publishEvent(any(OfferCreatedEvent.class));
    }

    @Test
    void patch_notFound_throwsNotFound() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.patch(UUID.randomUUID(),
            new OfferPatchDto("X", null, null, null, null, null, null, null, null, null, null, null, null)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void patch_success_appliesFields() {
        Offer o = offer();
        when(repository.findById(any())).thenReturn(Optional.of(o));
        service.patch(o.getId(), new OfferPatchDto("Renommé", "d", null, null, null, null, true, "bonus", 20, true, "i", null, null));
        assertThat(o.getTitle()).isEqualTo("Renommé");
        assertThat(o.getType()).isEqualTo("bonus");
    }

    @Test
    void patch_expiresBeforeStarts_throwsBadRequest() {
        Offer o = offer();
        when(repository.findById(any())).thenReturn(Optional.of(o));
        assertThatThrownBy(() -> service.patch(o.getId(),
            new OfferPatchDto(null, null, null, start.minusSeconds(3600), null, null, null, null, null, null, null, null, null)))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void patch_invalidType_throwsBadRequest() {
        Offer o = offer();
        when(repository.findById(any())).thenReturn(Optional.of(o));
        assertThatThrownBy(() -> service.patch(o.getId(),
            new OfferPatchDto(null, null, null, null, null, null, null, "bogus", null, null, null, null, null)))
            .isInstanceOf(BadRequestException.class);
    }

    // ─── ITEM 2 — is_pinned : PATCH applique le flag + DTO le renvoie ─────────

    @Test
    void patch_setsPinned_andDtoReflectsIt() {
        Offer o = offer();
        assertThat(o.isPinned()).isFalse(); // défaut
        when(repository.findById(any())).thenReturn(Optional.of(o));
        OfferDto dto = service.patch(o.getId(),
            new OfferPatchDto(null, null, null, null, null, null, null, null, null, null, null, null, true));
        assertThat(o.isPinned()).isTrue();
        assertThat(dto.isPinned()).isTrue();
    }

    @Test
    void patch_nullIsPinned_leavesPinnedUnchanged() {
        Offer o = offer();
        o.setPinned(true); // déjà épinglée
        when(repository.findById(any())).thenReturn(Optional.of(o));
        // isPinned null → pas de modification (sémantique PATCH)
        OfferDto dto = service.patch(o.getId(),
            new OfferPatchDto("Titre", null, null, null, null, null, null, null, null, null, null, null, null));
        assertThat(o.isPinned()).isTrue();
        assertThat(dto.isPinned()).isTrue();
    }

    @Test
    void softDelete_notFoundAndSuccess() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.softDelete(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        Offer o = offer();
        when(repository.findById(o.getId())).thenReturn(Optional.of(o));
        service.softDelete(o.getId());
        assertThat(o.getDeletedAt()).isNotNull();
    }

    // ─── Gap 2 — campaign_id persisté à la création ───────────────────────────

    @Test
    void create_persistsCampaignId() {
        UUID campaignId = UUID.randomUUID();
        OfferCreateDto dto = new OfferCreateDto(UUID.randomUUID(), "Promo", "desc", start, end,
            null, null, "promo", null, true, "img", null, campaignId);
        OfferDto result = service.create(dto);
        assertThat(result.campaignId()).isEqualTo(campaignId);
    }

    // ─── Gap 1 — impressions (record append-only + stats) ─────────────────────

    @Test
    void recordImpression_offerNotFound_throwsNotFound() {
        when(repository.existsById(any())).thenReturn(false);
        assertThatThrownBy(() -> service.recordImpression(UUID.randomUUID(), "view"))
            .isInstanceOf(NotFoundException.class);
        verify(impressionRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void recordImpression_defaultsTypeToView_whenBlank() {
        UUID offerId = UUID.randomUUID();
        when(repository.existsById(offerId)).thenReturn(true);
        service.recordImpression(offerId, "   ");
        ArgumentCaptor<OfferImpression> captor = ArgumentCaptor.forClass(OfferImpression.class);
        verify(impressionRepository).save(captor.capture());
        assertThat(captor.getValue().getOfferId()).isEqualTo(offerId);
        assertThat(captor.getValue().getImpressionType()).isEqualTo("view");
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    void recordImpression_usesProvidedType() {
        UUID offerId = UUID.randomUUID();
        when(repository.existsById(offerId)).thenReturn(true);
        service.recordImpression(offerId, "click");
        ArgumentCaptor<OfferImpression> captor = ArgumentCaptor.forClass(OfferImpression.class);
        verify(impressionRepository).save(captor.capture());
        assertThat(captor.getValue().getImpressionType()).isEqualTo("click");
    }

    @Test
    void listImpressions_delegatesToRepository() {
        when(impressionRepository.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(any()))
            .thenReturn(List.of());
        assertThat(service.listImpressions(30)).isEmpty();
        verify(impressionRepository).findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(any());
    }

    // ─── Périmètre tenant (fuite de périmètre) ────────────────────────────────
    // Les offres n'ont pas de tenant_id : tenant déduit via restaurant. Un client
    // oneclick non-membre ne voit que les offres des restos {public} ∪ memberships.

    @Test
    @SuppressWarnings("unchecked")
    void findAll_scoped_usesNativeJoinFilter_whenVisibleNotNull() {
        Set<UUID> visible = Set.of(UUID.randomUUID());
        when(tenantScope.visibleTenantIdsOrNull()).thenReturn(visible);
        when(repository.findAllScoped(eq(visible), any(), org.mockito.ArgumentMatchers.anyBoolean(),
                any(), any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(Page.empty());

        assertThat(service.findAll(null, true, 0, 20).getContent()).isEmpty();

        // Scopé → passe par le finder natif JOIN restaurants, JAMAIS par le finder Specification legacy.
        verify(repository).findAllScoped(eq(visible), org.mockito.ArgumentMatchers.isNull(), eq(true),
            any(), any(org.springframework.data.domain.Pageable.class));
        verify(repository, org.mockito.Mockito.never())
            .findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAll_bypass_usesLegacySpecification_whenVisibleNull() {
        // SUPERADMIN (cross-tenant) → null → aucun filtre tenant, comportement legacy.
        when(tenantScope.visibleTenantIdsOrNull()).thenReturn(null);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        assertThat(service.findAll(UUID.randomUUID(), false, 0, 20).getContent()).isEmpty();

        verify(repository).findAll(any(Specification.class), any(Pageable.class));
        verify(repository, org.mockito.Mockito.never())
            .findAllScoped(any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any(),
                any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void findById_outOfScope_throwsNotFound() {
        // Offre d'un tenant hors périmètre → 404 (pas de divulgation de l'existence).
        Offer o = offer();
        UUID tenantOfOffer = UUID.randomUUID();
        when(tenantScope.visibleTenantIdsOrNull()).thenReturn(Set.of(UUID.randomUUID())); // caller scopé (non SUPERADMIN)
        when(repository.findById(o.getId())).thenReturn(Optional.of(o));
        when(repository.findTenantIdOfOffer(o.getId())).thenReturn(tenantOfOffer);
        when(tenantScope.canSeeTenant(tenantOfOffer)).thenReturn(false);

        assertThatThrownBy(() -> service.findById(o.getId())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void recordImpression_outOfScope_throwsNotFound_andDoesNotSave() {
        UUID offerId = UUID.randomUUID();
        UUID tenantOfOffer = UUID.randomUUID();
        when(tenantScope.visibleTenantIdsOrNull()).thenReturn(Set.of(UUID.randomUUID())); // caller scopé (non SUPERADMIN)
        when(repository.existsById(offerId)).thenReturn(true);
        when(repository.findTenantIdOfOffer(offerId)).thenReturn(tenantOfOffer);
        when(tenantScope.canSeeTenant(tenantOfOffer)).thenReturn(false);

        assertThatThrownBy(() -> service.recordImpression(offerId, "view"))
            .isInstanceOf(NotFoundException.class);
        verify(impressionRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void search_bypass_delegatesUnscoped_whenVisibleNull() {
        // SUPERADMIN → recherche globale (Searchable.execute sur le repo, JAMAIS de contrainte resto).
        when(tenantScope.visibleTenantIdsOrNull()).thenReturn(null);
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        SearchRequest req = new SearchRequest(List.of(), null, 0, 20);
        assertThat(service.search(req, Set.of("title")).getContent()).isEmpty();

        verify(repository, org.mockito.Mockito.never()).findRestaurantIdsInTenants(any());
    }

    @Test
    void search_scoped_constrainsToVisibleRestaurants_whenVisibleNotNull() {
        Set<UUID> visible = Set.of(UUID.randomUUID());
        when(tenantScope.visibleTenantIdsOrNull()).thenReturn(visible);
        when(repository.findRestaurantIdsInTenants(visible)).thenReturn(List.of(UUID.randomUUID()));
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        SearchRequest req = new SearchRequest(List.of(), null, 0, 20);
        assertThat(service.search(req, Set.of("title")).getContent()).isEmpty();

        // Résolution du périmètre puis exécution scopée (Specification + contrainte resto).
        verify(repository).findRestaurantIdsInTenants(visible);
        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void search_scoped_noVisibleRestaurant_returnsEmpty_noEnumeration() {
        // Client sans aucun restaurant visible (cas dégénéré) → page vide, pas de scan cross-tenant.
        Set<UUID> visible = Set.of(UUID.randomUUID());
        when(tenantScope.visibleTenantIdsOrNull()).thenReturn(visible);
        when(repository.findRestaurantIdsInTenants(visible)).thenReturn(List.of());

        SearchRequest req = new SearchRequest(List.of(), null, 0, 20);
        assertThat(service.search(req, Set.of("title")).getContent()).isEmpty();

        verify(repository, org.mockito.Mockito.never()).findAll(any(Specification.class), any(Pageable.class));
    }
}
