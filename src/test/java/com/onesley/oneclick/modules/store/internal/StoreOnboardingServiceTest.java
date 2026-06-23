package com.onesley.oneclick.modules.store.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.core.identity.api.UserProvisioningApi;
import com.onesley.oneclick.core.tenant.api.TenantDirectoryApi;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.restaurant.api.RestaurantProvisioningApi;
import com.onesley.oneclick.modules.store.api.StoreOnboardingDtos.OnboardingCreateDto;
import com.onesley.oneclick.modules.store.api.StoreOnboardingDtos.OnboardingDecisionDto;
import com.onesley.oneclick.shared.events.StoreOnboardingDecidedEvent;
import com.onesley.oneclick.shared.events.StoreOnboardingRequestedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

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
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class StoreOnboardingServiceTest {

    @Mock StoreOnboardingRepository repo;
    @Mock UserDirectoryApi userDirectory;
    @Mock UserProvisioningApi userProvisioning;
    @Mock RestaurantProvisioningApi restaurantProvisioning;
    @Mock TenantDirectoryApi tenantDirectory;
    @Mock StoreOnboardingPublisher publisher;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks StoreOnboardingService service;

    private StoreOnboardingRequest request() {
        StoreOnboardingRequest r = new StoreOnboardingRequest();
        r.setRestaurantName("Resto"); r.setOwnerEmail("o@x.ma"); r.setStatus("pending");
        r.setOwnerFirstName("Ada"); r.setOwnerLastName("Lovelace");
        return r;
    }

    @Test
    void findAll_withStatus_andWithout() {
        when(repo.findByStatus("pending")).thenReturn(List.of(request()));
        when(repo.findAllActive()).thenReturn(List.of(request(), request()));
        assertThat(service.findAll("pending")).hasSize(1);
        assertThat(service.findAll(null)).hasSize(2);
    }

    @Test
    void findById_notFoundAndFound() {
        when(repo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        StoreOnboardingRequest r = request();
        when(repo.findById(r.getId())).thenReturn(Optional.of(r));
        assertThat(service.findById(r.getId())).isNotNull();
    }

    @Test
    void create_success_persistsEnrollmentFields() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        var saved = service.create(new OnboardingCreateDto(UUID.randomUUID(), "Bistrot", "marocaine", "Casa",
            "12 rue X", "+212600", "Ada", "L", "ada@x.ma", "+212611",
            "€€", "Bistrot de quartier", "Propriétaire", "001234567890123", "12345678", "RC-1", "PAT-1", 80,
            List.of("Déjeuner", "Dîner")));
        assertThat(saved).isNotNull();
        // Les champs enrollment legacy (V58) doivent être persistés + exposés.
        assertThat(saved.ice()).isEqualTo("001234567890123");
        assertThat(saved.capacity()).isEqualTo(80);
        assertThat(saved.services()).containsExactly("Déjeuner", "Dîner");
    }

    // ─── B4 — création → event admin (in-app) ──────────────────────────────────────────────────

    @Test
    void create_publishesRequestedEvent_withResolvedAdmins_andContactName() {
        // id @GeneratedValue → assigné par JPA en réel ; le mock save doit le simuler pour que
        // l'event porte un requestId non-null (l'intégration couvre la vraie persistance).
        when(repo.save(any())).thenAnswer(i -> {
            StoreOnboardingRequest e = i.getArgument(0);
            ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
            return e;
        });
        UUID admin1 = UUID.randomUUID(), admin2 = UUID.randomUUID();
        when(userDirectory.adminUserIds()).thenReturn(List.of(admin1, admin2));

        service.create(new OnboardingCreateDto(UUID.randomUUID(), "Bistrot", "marocaine", "Casa",
            "12 rue X", "+212600", "Ada", "Lovelace", "ada@x.ma", "+212611",
            null, null, null, null, null, null, null, null, null));

        ArgumentCaptor<StoreOnboardingRequestedEvent> cap =
            ArgumentCaptor.forClass(StoreOnboardingRequestedEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        StoreOnboardingRequestedEvent ev = cap.getValue();
        assertThat(ev.recipientAdminIds()).containsExactly(admin1, admin2);
        assertThat(ev.restaurantName()).isEqualTo("Bistrot");
        assertThat(ev.contactName()).isEqualTo("Ada Lovelace");
        assertThat(ev.requestId()).isNotNull();
        // BE-5 — la nouvelle demande est aussi poussée sur la file admin temps réel.
        verify(publisher).publishNew(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void create_publishesRequestedEvent_evenWhenNoAdmins() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userDirectory.adminUserIds()).thenReturn(List.of()); // aucun SUPERADMIN actif

        service.create(new OnboardingCreateDto(UUID.randomUUID(), "Bistrot", null, null,
            null, null, "Ada", "L", "ada@x.ma", null,
            null, null, null, null, null, null, null, null, null));

        ArgumentCaptor<StoreOnboardingRequestedEvent> cap =
            ArgumentCaptor.forClass(StoreOnboardingRequestedEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        // Event publié quand même (le listener saute si la liste est vide) — pas de NPE.
        assertThat(cap.getValue().recipientAdminIds()).isEmpty();
    }

    // ─── B5 — décision → event email (gérant) ──────────────────────────────────────────────────

    @Test
    void decide_notFound_andSuccess() {
        when(repo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.decide(UUID.randomUUID(), new OnboardingDecisionDto("approved", null, UUID.randomUUID())))
            .isInstanceOf(NotFoundException.class);
        StoreOnboardingRequest r = request();
        when(repo.findById(r.getId())).thenReturn(Optional.of(r));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        service.decide(r.getId(), new OnboardingDecisionDto("rejected", "incomplet", UUID.randomUUID()));
        assertThat(r.getStatus()).isEqualTo("rejected");
        assertThat(r.getReviewedAt()).isNotNull();
    }

    @Test
    void decide_approved_provisionsOwnerAndRestaurant_publishesDecidedEvent_withLoginLinkAndTempPassword() {
        ReflectionTestUtils.setField(service, "businessBaseUrl", "https://app.test");
        StoreOnboardingRequest r = request();
        UUID tenantId = UUID.randomUUID(), ownerId = UUID.randomUUID(), restoId = UUID.randomUUID();
        when(repo.findById(r.getId())).thenReturn(Optional.of(r));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        // tenantId null sur la demande → fallback tenant public oneclick.
        when(tenantDirectory.findIdBySlug("oneclick")).thenReturn(Optional.of(tenantId));
        when(userProvisioning.provisionOwner(any())).thenReturn(ownerId);
        when(restaurantProvisioning.provisionWithOwner(any())).thenReturn(restoId);

        service.decide(r.getId(), new OnboardingDecisionDto("approved", null, UUID.randomUUID()));

        // BE-2 — provisioning des 2 entités + ancrage sur la demande.
        ArgumentCaptor<UserProvisioningApi.ProvisionOwnerCommand> ownerCap =
            ArgumentCaptor.forClass(UserProvisioningApi.ProvisionOwnerCommand.class);
        verify(userProvisioning).provisionOwner(ownerCap.capture());
        assertThat(ownerCap.getValue().tenantId()).isEqualTo(tenantId);
        assertThat(ownerCap.getValue().email()).isEqualTo("o@x.ma");
        assertThat(ownerCap.getValue().rawPassword()).isNotBlank();
        ArgumentCaptor<RestaurantProvisioningApi.ProvisionCommand> restoCap =
            ArgumentCaptor.forClass(RestaurantProvisioningApi.ProvisionCommand.class);
        verify(restaurantProvisioning).provisionWithOwner(restoCap.capture());
        assertThat(restoCap.getValue().ownerUserId()).isEqualTo(ownerId);
        assertThat(restoCap.getValue().tenantId()).isEqualTo(tenantId);
        assertThat(r.getProvisionedUserId()).isEqualTo(ownerId);
        assertThat(r.getProvisionedRestaurantId()).isEqualTo(restoId);

        ArgumentCaptor<StoreOnboardingDecidedEvent> cap =
            ArgumentCaptor.forClass(StoreOnboardingDecidedEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        StoreOnboardingDecidedEvent ev = cap.getValue();
        assertThat(ev.approved()).isTrue();
        assertThat(ev.contactEmail()).isEqualTo("o@x.ma");
        assertThat(ev.contactName()).isEqualTo("Ada Lovelace");
        assertThat(ev.businessName()).isEqualTo("Resto");
        assertThat(ev.rejectionReason()).isNull();
        assertThat(ev.loginUrl()).isEqualTo("https://app.test/login");
        // BE-2 — le mdp temporaire (envoyé dans l'email) correspond à celui passé au provisioning.
        assertThat(ev.tempPassword()).isNotBlank().isEqualTo(ownerCap.getValue().rawPassword());
    }

    @Test
    void decide_approved_idempotent_whenAlreadyProvisioned_noReProvision() {
        StoreOnboardingRequest r = request();
        r.setStatus("approved");
        r.setProvisionedUserId(UUID.randomUUID()); // déjà approuvée + provisionnée
        when(repo.findById(r.getId())).thenReturn(Optional.of(r));

        var dto = service.decide(r.getId(), new OnboardingDecisionDto("approved", null, UUID.randomUUID()));

        assertThat(dto.status()).isEqualTo("approved");
        // No-op : aucun re-provisioning ni nouvel email.
        verify(userProvisioning, never()).provisionOwner(any());
        verify(restaurantProvisioning, never()).provisionWithOwner(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void decide_rejected_publishesDecidedEvent_withReason_noLink() {
        StoreOnboardingRequest r = request();
        when(repo.findById(r.getId())).thenReturn(Optional.of(r));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.decide(r.getId(), new OnboardingDecisionDto("rejected", "Dossier incomplet", UUID.randomUUID()));

        ArgumentCaptor<StoreOnboardingDecidedEvent> cap =
            ArgumentCaptor.forClass(StoreOnboardingDecidedEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        StoreOnboardingDecidedEvent ev = cap.getValue();
        assertThat(ev.approved()).isFalse();
        assertThat(ev.rejectionReason()).isEqualTo("Dossier incomplet");
        assertThat(ev.loginUrl()).isNull();
    }
}
