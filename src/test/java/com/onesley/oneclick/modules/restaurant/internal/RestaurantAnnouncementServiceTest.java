package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.RestaurantAccessGuard;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantAnnouncementDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests unitaires Mockito de {@link RestaurantAnnouncementService} (Gap #6). */
@ExtendWith(MockitoExtension.class)
class RestaurantAnnouncementServiceTest {

    @Mock RestaurantAnnouncementRepository repository;
    @Mock RestaurantAccessGuard accessGuard;
    @Mock RestaurantRepository restaurantRepository;
    @Mock com.onesley.oneclick.security.TenantScope tenantScope;

    private static final Instant NOW = Instant.parse("2026-06-07T10:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final UUID restaurantId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();

    private RestaurantAnnouncementService service() {
        return new RestaurantAnnouncementService(repository, accessGuard, clock, restaurantRepository, tenantScope);
    }

    /** Stub : le resto existe, son tenant est visible par le caller → getActive atteint le repo d'annonces. */
    private void stubVisibleRestaurant() {
        Restaurant r = new Restaurant(UUID.randomUUID(),
            new com.onesley.oneclick.core.tenant.api.Tenant(UUID.randomUUID(), "T", "t"), "R", "C");
        org.springframework.test.util.ReflectionTestUtils.setField(r, "tenantId", UUID.randomUUID());
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(r));
        when(tenantScope.canSeeTenant(any())).thenReturn(true);
    }

    @Test
    void getActive_none_returnsNull() {
        stubVisibleRestaurant();
        when(repository.findFirstByRestaurantIdAndExpiresAtAfterOrderByCreatedAtDesc(restaurantId, NOW))
            .thenReturn(Optional.empty());
        assertThat(service().getActive(restaurantId)).isNull();
    }

    @Test
    void getActive_present_returnsDto() {
        stubVisibleRestaurant();
        RestaurantAnnouncement a = new RestaurantAnnouncement(
            UUID.randomUUID(), restaurantId, "Fermé dimanche", authorId, NOW, NOW.plus(Duration.ofHours(24)));
        when(repository.findFirstByRestaurantIdAndExpiresAtAfterOrderByCreatedAtDesc(restaurantId, NOW))
            .thenReturn(Optional.of(a));
        RestaurantAnnouncementDto dto = service().getActive(restaurantId);
        assertThat(dto).isNotNull();
        assertThat(dto.message()).isEqualTo("Fermé dimanche");
    }

    @Test
    void create_setsExpiry24h_authorCurrentUser_andExpiresOthers() {
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(authorId);

            RestaurantAnnouncementDto dto = service().create(restaurantId, "  Cuisine ouverte minuit  ");

            ArgumentCaptor<RestaurantAnnouncement> c = ArgumentCaptor.forClass(RestaurantAnnouncement.class);
            verify(repository).save(c.capture());
            assertThat(c.getValue().getMessage()).isEqualTo("Cuisine ouverte minuit"); // trim
            assertThat(c.getValue().getRestaurantId()).isEqualTo(restaurantId);
            assertThat(c.getValue().getAuthorId()).isEqualTo(authorId);
            assertThat(c.getValue().getCreatedAt()).isEqualTo(NOW);
            assertThat(c.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
            // single-active : expire les autres annonces du resto
            verify(repository).expireOthers(eq(restaurantId), any(UUID.class), eq(NOW));
            assertThat(dto.message()).isEqualTo("Cuisine ouverte minuit");
        }
    }

    @Test
    void create_requiresStaffOrAdmin_abacGuard() {
        org.mockito.Mockito.doThrow(new com.onesley.oneclick.exception.ForbiddenException("no"))
            .when(accessGuard).requireAdminOrActiveStaffOf(restaurantId);
        assertThatThrownBy(() -> service().create(restaurantId, "x"))
            .isInstanceOf(com.onesley.oneclick.exception.ForbiddenException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void create_blankMessage_throwsBadRequest() {
        assertThatThrownBy(() -> service().create(restaurantId, "   "))
            .isInstanceOf(BadRequestException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void create_messageTooLong_throwsBadRequest() {
        String tooLong = "x".repeat(281);
        assertThatThrownBy(() -> service().create(restaurantId, tooLong))
            .isInstanceOf(BadRequestException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void delete_existing_abacThenDelete() {
        UUID id = UUID.randomUUID();
        RestaurantAnnouncement a = new RestaurantAnnouncement(id, restaurantId, "m", authorId, NOW, NOW.plus(Duration.ofHours(24)));
        when(repository.findById(id)).thenReturn(Optional.of(a));
        service().delete(id);
        verify(accessGuard).requireAdminOrActiveStaffOf(restaurantId);
        verify(repository).delete(a);
    }

    @Test
    void delete_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().delete(id)).isInstanceOf(NotFoundException.class);
        verify(repository, never()).delete(any());
    }
}
