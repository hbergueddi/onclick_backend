package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.core.notification.api.NotificationDtos.CampaignCreateDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.DeviceTokenCreateDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.NotificationCreateDto;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.SecurityHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link NotificationService} (L3 — core.notification).
 * Notifications / campaigns / device tokens CRUD + cloche (findByUser, unread, markAllRead) + RBAC.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    @Mock NotificationRepository notifRepo;
    @Mock NotificationCampaignRepository campaignRepo;
    @Mock DeviceTokenRepository tokenRepo;
    @Mock ApplicationEventPublisher events;
    @InjectMocks NotificationService service;

    @BeforeEach
    void setup() {
        lenient().when(notifRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(campaignRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(tokenRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private Notification notif() {
        return new Notification(UUID.randomUUID(), UUID.randomUUID(), "reservation", "inapp", "Titre", "Corps");
    }
    private DeviceToken token() {
        return new DeviceToken(UUID.randomUUID(), UUID.randomUUID(), "tok", "ios");
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAll_filtersAndNoFilters() {
        when(notifRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        assertThat(service.findAll(UUID.randomUUID(), true, 0, 20).getContent()).isEmpty();
        assertThat(service.findAll(null, false, 0, 20).getContent()).isEmpty();
    }

    @Test
    void create_withChannelAndDefault() {
        assertThat(service.create(new NotificationCreateDto(UUID.randomUUID(), "promotion", "push", "T", "B", "/link"))).isNotNull();
        assertThat(service.create(new NotificationCreateDto(UUID.randomUUID(), "system", null, "T", "B", null))).isNotNull();
    }

    @Test
    void markRead_notFound_andAlreadyRead_andUnread() {
        when(notifRepo.findById(any())).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThatThrownBy(() -> service.markRead(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        }
        Notification n = notif();
        when(notifRepo.findById(n.getId())).thenReturn(Optional.of(n));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            service.markRead(n.getId());
            assertThat(n.getReadAt()).isNotNull();
            service.markRead(n.getId()); // déjà lue → pas de re-mark
        }
    }

    @Test
    void findByUser_unreadOnly_andAll() {
        when(notifRepo.findAllByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(any())).thenReturn(List.of(notif()));
        when(notifRepo.findAllByRecipientUserIdOrderByCreatedAtDesc(any())).thenReturn(List.of(notif(), notif()));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThat(service.findByUser(UUID.randomUUID(), true)).hasSize(1);
            assertThat(service.findByUser(UUID.randomUUID(), false)).hasSize(2);
        }
    }

    @Test
    void unreadCount_andMarkAllRead() {
        when(notifRepo.countByRecipientUserIdAndReadAtIsNull(any())).thenReturn(7L);
        when(notifRepo.markAllReadByRecipientUserId(any(), any())).thenReturn(7);
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThat(service.unreadCountByUser(UUID.randomUUID()).count()).isEqualTo(7L);
            assertThat(service.markAllReadByUser(UUID.randomUUID()).updated()).isEqualTo(7L);
        }
    }

    @Test
    void campaigns_findAndCreate() {
        when(campaignRepo.findAllByTenantId(any())).thenReturn(List.of(
            new NotificationCampaign(UUID.randomUUID(), UUID.randomUUID(), "T", "M")));
        assertThat(service.findCampaignsByTenant(UUID.randomUUID())).hasSize(1);
        assertThat(service.createCampaign(new CampaignCreateDto(UUID.randomUUID(), "T", "M", "ruby", Instant.now()))).isNotNull();
        assertThat(service.createCampaign(new CampaignCreateDto(UUID.randomUUID(), "T", "M", null, null))).isNotNull();
    }

    @Test
    void tokens_find_register_existing_new_unregister() {
        when(tokenRepo.findAllByUserId(any())).thenReturn(List.of(token()));
        assertThat(service.findTokensByUser(UUID.randomUUID())).hasSize(1);

        // token déjà enregistré → renvoie l'existant
        DeviceToken existing = token();
        when(tokenRepo.findByToken(eq("tok"))).thenReturn(Optional.of(existing));
        assertThat(service.registerToken(new DeviceTokenCreateDto(UUID.randomUUID(), "tok", "ios", "ma.oneclick.win"))).isNotNull();

        // token absent → crée
        when(tokenRepo.findByToken(eq("new"))).thenReturn(Optional.empty());
        assertThat(service.registerToken(new DeviceTokenCreateDto(UUID.randomUUID(), "new", "android", null))).isNotNull();

        // unregister not found
        when(tokenRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.unregisterToken(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        // unregister success
        DeviceToken t = token();
        when(tokenRepo.findById(t.getId())).thenReturn(Optional.of(t));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            service.unregisterToken(t.getId());
            verify(tokenRepo).delete(t);
        }
    }
}
