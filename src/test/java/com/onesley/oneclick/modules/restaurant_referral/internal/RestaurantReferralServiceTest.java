package com.onesley.oneclick.modules.restaurant_referral.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.core.tenant.api.TenantDirectoryApi;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.events.RestaurantReferralActivatedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link RestaurantReferralService} (parrainage owner→owner, V100).
 *
 * <p>Couvre : {@code getOrCreateMyCode} (création + idempotence), {@code activate} (happy path →
 * event + récompense + push, self-referral, code inconnu, double-activation, tenant non-oneclick).
 * 100 % isolé : repository / TenantDirectoryApi / publisher / event-publisher mockés, SecurityHelper
 * statique mocké, Clock figé.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RestaurantReferralServiceTest {

    @Mock RestaurantReferralRepository repository;
    @Mock TenantDirectoryApi tenantDirectory;
    @Mock UserDirectoryApi userDirectory;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock RestaurantReferralDashboardPublisher dashboardPublisher;

    RestaurantReferralService service;

    private MockedStatic<SecurityHelper> securityMock;

    private static final int REWARD = 100;
    private final UUID oneclickTenant = UUID.randomUUID();
    private final UUID otherTenant = UUID.randomUUID();
    private final UUID referrerResto = UUID.randomUUID();
    private final UUID refereeResto = UUID.randomUUID();
    private final UUID referrerOwner = UUID.randomUUID();
    private final UUID refereeOwner = UUID.randomUUID();
    private final UUID admin1 = UUID.randomUUID();
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-17T10:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setup() {
        service = new RestaurantReferralService(
            repository, tenantDirectory, userDirectory, eventPublisher, dashboardPublisher, clock);
        ReflectionTestUtils.setField(service, "rewardPoints", REWARD);

        securityMock = org.mockito.Mockito.mockStatic(SecurityHelper.class);
        securityMock.when(SecurityHelper::currentUserId).thenReturn(refereeOwner);
        securityMock.when(SecurityHelper::isAdmin).thenReturn(false);

        // oneclick tenant resolution
        lenient().when(tenantDirectory.slugById(oneclickTenant)).thenReturn(Optional.of("oneclick"));
        lenient().when(tenantDirectory.slugById(otherTenant)).thenReturn(Optional.of("homu"));
        lenient().when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(repository.findByReferralCodeIgnoreCase(any())).thenReturn(Optional.empty());
        // Lot B3 — admins plateforme résolus côté service et portés sur l'event.
        lenient().when(userDirectory.adminUserIds()).thenReturn(java.util.List.of(admin1));
    }

    @AfterEach
    void tearDown() {
        if (securityMock != null) securityMock.close();
    }

    private RestaurantReferral pendingCode() {
        return new RestaurantReferral(
            UUID.randomUUID(), referrerResto, referrerOwner, "RR-ABC234", oneclickTenant);
    }

    // ─── getOrCreateMyCode ────────────────────────────────────────────────────────────────────

    @Test
    void getOrCreateMyCode_createsNewCode_whenNoneExists() {
        securityMock.when(SecurityHelper::currentUserId).thenReturn(referrerOwner);
        when(repository.findTenantIdOfRestaurant(referrerResto)).thenReturn(Optional.of(oneclickTenant));
        when(repository.isOwnerOfRestaurant(referrerOwner, referrerResto)).thenReturn(true);
        when(repository.findByReferrerRestaurantId(referrerResto)).thenReturn(Optional.empty());

        var dto = service.getOrCreateMyCode(referrerResto);

        assertThat(dto.referrerRestaurantId()).isEqualTo(referrerResto);
        assertThat(dto.referrerUserId()).isEqualTo(referrerOwner);
        assertThat(dto.referralCode()).startsWith("RR-");
        assertThat(dto.status()).isEqualTo("pending");
        verify(repository).save(any(RestaurantReferral.class));
    }

    @Test
    void getOrCreateMyCode_returnsExisting_idempotent() {
        securityMock.when(SecurityHelper::currentUserId).thenReturn(referrerOwner);
        when(repository.findTenantIdOfRestaurant(referrerResto)).thenReturn(Optional.of(oneclickTenant));
        when(repository.isOwnerOfRestaurant(referrerOwner, referrerResto)).thenReturn(true);
        RestaurantReferral existing = pendingCode();
        when(repository.findByReferrerRestaurantId(referrerResto)).thenReturn(Optional.of(existing));

        var dto = service.getOrCreateMyCode(referrerResto);

        assertThat(dto.referralCode()).isEqualTo("RR-ABC234");
        verify(repository, never()).save(any());
    }

    @Test
    void getOrCreateMyCode_rejects_nonOwner() {
        securityMock.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
        when(repository.findTenantIdOfRestaurant(referrerResto)).thenReturn(Optional.of(oneclickTenant));
        when(repository.isOwnerOfRestaurant(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.getOrCreateMyCode(referrerResto))
            .isInstanceOf(ForbiddenException.class);
    }

    // ─── activate — happy path ────────────────────────────────────────────────────────────────

    @Test
    void activate_happyPath_creditsReferrer_publishesEvent_andPushes() {
        when(repository.findTenantIdOfRestaurant(refereeResto)).thenReturn(Optional.of(oneclickTenant));
        when(repository.isOwnerOfRestaurant(refereeOwner, refereeResto)).thenReturn(true);
        when(repository.findByReferralCodeIgnoreCase("RR-ABC234")).thenReturn(Optional.of(pendingCode()));
        when(repository.existsByRefereeRestaurantId(refereeResto)).thenReturn(false);

        var dto = service.activate("RR-ABC234", refereeResto);

        assertThat(dto.status()).isEqualTo("activated");
        assertThat(dto.refereeRestaurantId()).isEqualTo(refereeResto);
        assertThat(dto.refereeUserId()).isEqualTo(refereeOwner);
        assertThat(dto.rewardPoints()).isEqualTo(REWARD);
        assertThat(dto.activatedAt()).isEqualTo(Instant.parse("2026-06-17T10:00:00Z"));

        ArgumentCaptor<RestaurantReferralActivatedEvent> ev =
            ArgumentCaptor.forClass(RestaurantReferralActivatedEvent.class);
        verify(eventPublisher).publishEvent(ev.capture());
        // Récompense au PARRAIN seul (referrerUserId/referrerRestaurantId), pas le filleul.
        assertThat(ev.getValue().referrerUserId()).isEqualTo(referrerOwner);
        assertThat(ev.getValue().referrerRestaurantId()).isEqualTo(referrerResto);
        assertThat(ev.getValue().rewardPoints()).isEqualTo(REWARD);
        // Lot B3 — destinataires admin (SUPERADMIN) résolus côté service et portés sur l'event.
        assertThat(ev.getValue().recipientAdminIds()).containsExactly(admin1);
        verify(dashboardPublisher).pushNow();
    }

    @Test
    void activate_trimsAndIsCaseInsensitiveCode() {
        when(repository.findTenantIdOfRestaurant(refereeResto)).thenReturn(Optional.of(oneclickTenant));
        when(repository.isOwnerOfRestaurant(refereeOwner, refereeResto)).thenReturn(true);
        when(repository.findByReferralCodeIgnoreCase("RR-ABC234")).thenReturn(Optional.of(pendingCode()));
        when(repository.existsByRefereeRestaurantId(refereeResto)).thenReturn(false);

        var dto = service.activate("  RR-ABC234  ", refereeResto);
        assertThat(dto.status()).isEqualTo("activated");
    }

    // ─── activate — rejets ────────────────────────────────────────────────────────────────────

    @Test
    void activate_rejects_selfReferral() {
        when(repository.findTenantIdOfRestaurant(referrerResto)).thenReturn(Optional.of(oneclickTenant));
        when(repository.isOwnerOfRestaurant(refereeOwner, referrerResto)).thenReturn(true);
        when(repository.findByReferralCodeIgnoreCase("RR-ABC234")).thenReturn(Optional.of(pendingCode()));

        // filleul == resto parrain
        assertThatThrownBy(() -> service.activate("RR-ABC234", referrerResto))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("lui-même");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void activate_rejects_unknownCode() {
        when(repository.findTenantIdOfRestaurant(refereeResto)).thenReturn(Optional.of(oneclickTenant));
        when(repository.isOwnerOfRestaurant(refereeOwner, refereeResto)).thenReturn(true);
        when(repository.findByReferralCodeIgnoreCase("RR-NOPE99")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activate("RR-NOPE99", refereeResto))
            .isInstanceOf(NotFoundException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void activate_rejects_doubleActivation_codeAlreadyActivated() {
        when(repository.findTenantIdOfRestaurant(refereeResto)).thenReturn(Optional.of(oneclickTenant));
        when(repository.isOwnerOfRestaurant(refereeOwner, refereeResto)).thenReturn(true);
        RestaurantReferral already = pendingCode();
        already.activate(UUID.randomUUID(), UUID.randomUUID(), REWARD, Instant.now(clock));
        when(repository.findByReferralCodeIgnoreCase("RR-ABC234")).thenReturn(Optional.of(already));

        assertThatThrownBy(() -> service.activate("RR-ABC234", refereeResto))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("déjà été activé");
    }

    @Test
    void activate_rejects_refereeAlreadyReferred() {
        when(repository.findTenantIdOfRestaurant(refereeResto)).thenReturn(Optional.of(oneclickTenant));
        when(repository.isOwnerOfRestaurant(refereeOwner, refereeResto)).thenReturn(true);
        when(repository.findByReferralCodeIgnoreCase("RR-ABC234")).thenReturn(Optional.of(pendingCode()));
        when(repository.existsByRefereeRestaurantId(refereeResto)).thenReturn(true);

        assertThatThrownBy(() -> service.activate("RR-ABC234", refereeResto))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("déjà été parrainé");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void activate_rejects_nonOneclickRefereeTenant() {
        when(repository.findTenantIdOfRestaurant(refereeResto)).thenReturn(Optional.of(otherTenant));

        assertThatThrownBy(() -> service.activate("RR-ABC234", refereeResto))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("OneClick");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void activate_rejects_referrerCodeFromNonOneclickTenant() {
        when(repository.findTenantIdOfRestaurant(refereeResto)).thenReturn(Optional.of(oneclickTenant));
        when(repository.isOwnerOfRestaurant(refereeOwner, refereeResto)).thenReturn(true);
        RestaurantReferral foreign = new RestaurantReferral(
            UUID.randomUUID(), referrerResto, referrerOwner, "RR-ABC234", otherTenant);
        when(repository.findByReferralCodeIgnoreCase("RR-ABC234")).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.activate("RR-ABC234", refereeResto))
            .isInstanceOf(ForbiddenException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }
}
