package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.core.notification.api.NotificationDtos.StaffNotificationPrefsDto;
import com.onesley.oneclick.exception.UnauthorizedException;
import com.onesley.oneclick.security.SecurityHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests unitaires Mockito de {@link StaffNotificationPreferenceService} (Gap #5). */
@ExtendWith(MockitoExtension.class)
class StaffNotificationPreferenceServiceTest {

    @Mock StaffNotificationPreferenceRepository repository;
    @InjectMocks StaffNotificationPreferenceService service;

    private final UUID userId = UUID.randomUUID();

    @Test
    void getMine_noRow_returnsAllEnabledDefaults() {
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(userId);
            when(repository.findByUserId(userId)).thenReturn(Optional.empty());

            StaffNotificationPrefsDto dto = service.getMine();

            assertThat(dto.booking()).isTrue();
            assertThat(dto.reservation()).isTrue();
            assertThat(dto.feedback()).isTrue();
            assertThat(dto.loyalty()).isTrue();
            assertThat(dto.system()).isTrue();
            verify(repository, never()).save(any());
        }
    }

    @Test
    void getMine_existingRow_returnsPersistedToggles() {
        StaffNotificationPreference pref = new StaffNotificationPreference(UUID.randomUUID(), userId);
        pref.setBooking(false);
        pref.setLoyalty(false);
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(userId);
            when(repository.findByUserId(userId)).thenReturn(Optional.of(pref));

            StaffNotificationPrefsDto dto = service.getMine();

            assertThat(dto.booking()).isFalse();
            assertThat(dto.loyalty()).isFalse();
            assertThat(dto.reservation()).isTrue();
            assertThat(dto.feedback()).isTrue();
            assertThat(dto.system()).isTrue();
        }
    }

    @Test
    void updateMine_noRow_createsNewRowWithToggles() {
        StaffNotificationPrefsDto input = new StaffNotificationPrefsDto(false, true, false, true, false);
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(userId);
            when(repository.findByUserId(userId)).thenReturn(Optional.empty());
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            StaffNotificationPrefsDto result = service.updateMine(input);

            ArgumentCaptor<StaffNotificationPreference> c = ArgumentCaptor.forClass(StaffNotificationPreference.class);
            verify(repository).save(c.capture());
            assertThat(c.getValue().getUserId()).isEqualTo(userId);
            assertThat(c.getValue().isBooking()).isFalse();
            assertThat(c.getValue().isReservation()).isTrue();
            assertThat(c.getValue().isFeedback()).isFalse();
            assertThat(c.getValue().isSystem()).isFalse();
            assertThat(result.booking()).isFalse();
        }
    }

    @Test
    void updateMine_existingRow_updatesInPlace() {
        StaffNotificationPreference existing = new StaffNotificationPreference(UUID.randomUUID(), userId);
        StaffNotificationPrefsDto input = new StaffNotificationPrefsDto(true, false, true, false, true);
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(userId);
            when(repository.findByUserId(userId)).thenReturn(Optional.of(existing));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.updateMine(input);

            assertThat(existing.isReservation()).isFalse();
            assertThat(existing.isLoyalty()).isFalse();
            assertThat(existing.isBooking()).isTrue();
            verify(repository).save(existing); // pas de nouvelle row → update in-place
        }
    }

    // ─── P1pref : isStaffCategoryEnabled (lookup non self-service, défaut ON) ──────

    @Test
    void isStaffCategoryEnabled_noRow_defaultsOn() {
        when(repository.findByUserId(userId)).thenReturn(Optional.empty());
        // aucune préférence enregistrée → toutes catégories ON (opt-out)
        assertThat(service.isStaffCategoryEnabled(userId, "booking")).isTrue();
        assertThat(service.isStaffCategoryEnabled(userId, "reservation")).isTrue();
        assertThat(service.isStaffCategoryEnabled(userId, "feedback")).isTrue();
        assertThat(service.isStaffCategoryEnabled(userId, "loyalty")).isTrue();
        assertThat(service.isStaffCategoryEnabled(userId, "system")).isTrue();
    }

    @Test
    void isStaffCategoryEnabled_existingRow_readsTheRightToggle() {
        StaffNotificationPreference pref = new StaffNotificationPreference(UUID.randomUUID(), userId);
        pref.setBooking(false);
        pref.setLoyalty(false);
        // les 3 autres restent true (défaut entité)
        when(repository.findByUserId(userId)).thenReturn(Optional.of(pref));

        assertThat(service.isStaffCategoryEnabled(userId, "booking")).isFalse();
        assertThat(service.isStaffCategoryEnabled(userId, "loyalty")).isFalse();
        assertThat(service.isStaffCategoryEnabled(userId, "reservation")).isTrue();
        assertThat(service.isStaffCategoryEnabled(userId, "feedback")).isTrue();
        assertThat(service.isStaffCategoryEnabled(userId, "system")).isTrue();
    }

    @Test
    void isStaffCategoryEnabled_unknownCategory_doesNotFilter() {
        StaffNotificationPreference pref = new StaffNotificationPreference(UUID.randomUUID(), userId);
        pref.setReservation(false);
        when(repository.findByUserId(userId)).thenReturn(Optional.of(pref));
        // catégorie inconnue → on ne filtre jamais à tort
        assertThat(service.isStaffCategoryEnabled(userId, "weird")).isTrue();
    }

    @Test
    void isStaffCategoryEnabled_nullArgs_defaultsOn_noLookup() {
        assertThat(service.isStaffCategoryEnabled(null, "reservation")).isTrue();
        assertThat(service.isStaffCategoryEnabled(userId, null)).isTrue();
        verify(repository, never()).findByUserId(any());
    }

    @Test
    void getMine_noAuth_throwsUnauthorized() {
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(null);
            assertThatThrownBy(() -> service.getMine()).isInstanceOf(UnauthorizedException.class);
            verify(repository, never()).findByUserId(any());
        }
    }

    @Test
    void updateMine_noAuth_throwsUnauthorized() {
        StaffNotificationPrefsDto input = StaffNotificationPrefsDto.allEnabled();
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(null);
            assertThatThrownBy(() -> service.updateMine(input)).isInstanceOf(UnauthorizedException.class);
            verify(repository, never()).save(any());
        }
    }
}
