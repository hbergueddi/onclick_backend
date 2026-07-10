package com.onesley.oneclick.modules.reservation.internal;

import com.onesley.oneclick.modules.reservation.api.ReservationDto;
import com.onesley.oneclick.security.SecurityHelper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link ReservationLookupTool} — outil métier (Tool Calling) lecture seule.
 * Valide : agit au nom de l'utilisateur connecté, formatage, cas vide / non authentifié, métadonnées.
 */
class ReservationLookupToolTest {

    private final ReservationService service = mock(ReservationService.class);
    private final ReservationLookupTool tool = new ReservationLookupTool(service);

    private static ReservationDto reservation(int guests, String status) {
        return new ReservationDto(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null,
            Instant.parse("2026-08-01T19:30:00Z"), guests, status, null, Instant.now(), false, null, null);
    }

    @Test
    void metadata_isStableAndParameterless() {
        assertThat(tool.name()).isEqualTo("get_my_reservations");
        assertThat(tool.parameters()).isEmpty();
        assertThat(tool.description()).isNotBlank();
    }

    @Test
    void execute_currentUser_formatsReservations() {
        UUID userId = UUID.randomUUID();
        Page<ReservationDto> page = new PageImpl<>(List.of(reservation(4, "confirmed")));
        when(service.findAll(any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(page);

        try (MockedStatic<SecurityHelper> sh = mockStatic(SecurityHelper.class)) {
            sh.when(SecurityHelper::currentUserId).thenReturn(userId);

            String out = tool.execute(java.util.Map.of());

            assertThat(out).contains("Réservations du client")
                .contains("4 pers.")
                .contains("confirmed");
        }
    }

    @Test
    void execute_noAuthenticatedUser_returnsMessage_withoutQuery() {
        try (MockedStatic<SecurityHelper> sh = mockStatic(SecurityHelper.class)) {
            sh.when(SecurityHelper::currentUserId).thenReturn(null);
            assertThat(tool.execute(java.util.Map.of())).contains("Aucun utilisateur authentifié");
        }
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void execute_empty_returnsFriendlyMessage() {
        when(service.findAll(any(), any(), any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(new PageImpl<>(List.of()));
        try (MockedStatic<SecurityHelper> sh = mockStatic(SecurityHelper.class)) {
            sh.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            assertThat(tool.execute(java.util.Map.of())).contains("aucune réservation");
        }
    }
}
