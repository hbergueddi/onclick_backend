package com.onesley.oneclick.modules.reservation.internal;

import com.onesley.oneclick.modules.reservation.api.ReservationCreateDto;
import com.onesley.oneclick.modules.reservation.api.ReservationDto;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.security.TenantScope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link ReservationBookingTool} — outil chatbot écriture (créer une réservation).
 * Vérifie les invariants de sécurité : client = user connecté, tenant résolu serveur, contrôle de périmètre.
 */
class ReservationBookingToolTest {

    private final ReservationService service = mock(ReservationService.class);
    private final ReservationRepository repository = mock(ReservationRepository.class);
    private final TenantScope tenantScope = mock(TenantScope.class);
    private final ReservationBookingTool tool = new ReservationBookingTool(service, repository, tenantScope);

    private static ReservationDto created(UUID tenantId, UUID clientId, UUID restaurantId) {
        return new ReservationDto(UUID.randomUUID(), tenantId, clientId, restaurantId, null, null,
            Instant.parse("2030-01-01T20:00:00Z"), 4, "pending", null, Instant.now(), false, null, null);
    }

    private Map<String, Object> validArgs(UUID restaurantId) {
        return Map.of("restaurant_id", restaurantId.toString(),
            "reservation_at", "2030-01-01T20:00:00Z", "guest_count", 4);
    }

    @Test
    void execute_happyPath_createsForCurrentUser_tenantResolvedServerSide() {
        UUID userId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(repository.findTenantIdByRestaurantId(restaurantId)).thenReturn(Optional.of(tenantId));
        when(tenantScope.canSeeTenant(tenantId)).thenReturn(true);
        when(service.create(any())).thenReturn(created(tenantId, userId, restaurantId));

        try (MockedStatic<SecurityHelper> sh = mockStatic(SecurityHelper.class)) {
            sh.when(SecurityHelper::currentUserId).thenReturn(userId);

            String out = tool.execute(validArgs(restaurantId));

            assertThat(out).contains("Réservation enregistrée").contains("pending");
            ArgumentCaptor<ReservationCreateDto> cap = ArgumentCaptor.forClass(ReservationCreateDto.class);
            verify(service).create(cap.capture());
            // Invariants sécurité : clientId = user connecté (jamais du LLM), tenantId = résolu serveur.
            assertThat(cap.getValue().clientId()).isEqualTo(userId);
            assertThat(cap.getValue().tenantId()).isEqualTo(tenantId);
            assertThat(cap.getValue().restaurantId()).isEqualTo(restaurantId);
        }
    }

    @Test
    void execute_noUser_refuses_withoutCreating() {
        try (MockedStatic<SecurityHelper> sh = mockStatic(SecurityHelper.class)) {
            sh.when(SecurityHelper::currentUserId).thenReturn(null);
            assertThat(tool.execute(validArgs(UUID.randomUUID()))).contains("Aucun utilisateur authentifié");
        }
        verifyNoInteractions(service);
    }

    @Test
    void execute_badRestaurantId_returnsMessage() {
        try (MockedStatic<SecurityHelper> sh = mockStatic(SecurityHelper.class)) {
            sh.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            assertThat(tool.execute(Map.of("restaurant_id", "not-a-uuid",
                "reservation_at", "2030-01-01T20:00:00Z", "guest_count", 2)))
                .contains("Identifiant de restaurant invalide");
        }
    }

    @Test
    void execute_restaurantNotFound_returnsMessage() {
        UUID restaurantId = UUID.randomUUID();
        when(repository.findTenantIdByRestaurantId(restaurantId)).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sh = mockStatic(SecurityHelper.class)) {
            sh.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            assertThat(tool.execute(validArgs(restaurantId))).contains("Restaurant introuvable");
        }
    }

    @Test
    void execute_tenantNotVisible_refuses() {
        UUID restaurantId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(repository.findTenantIdByRestaurantId(restaurantId)).thenReturn(Optional.of(tenantId));
        when(tenantScope.canSeeTenant(tenantId)).thenReturn(false);
        try (MockedStatic<SecurityHelper> sh = mockStatic(SecurityHelper.class)) {
            sh.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            assertThat(tool.execute(validArgs(restaurantId))).contains("n'est pas accessible");
        }
        verifyNoInteractions(service);
    }

    @Test
    void execute_acceptsOffsetAndLocalDatetimes() {
        // Un LLM émet souvent un offset (+02:00) ou une date-heure locale, pas seulement 'Z'.
        UUID restaurantId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(repository.findTenantIdByRestaurantId(restaurantId)).thenReturn(Optional.of(tenantId));
        when(tenantScope.canSeeTenant(tenantId)).thenReturn(true);
        when(service.create(any())).thenReturn(created(tenantId, UUID.randomUUID(), restaurantId));
        try (MockedStatic<SecurityHelper> sh = mockStatic(SecurityHelper.class)) {
            sh.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            for (String when : new String[]{"2030-01-01T20:30:00+02:00", "2030-01-01T20:30:00Z", "2030-01-01T20:30:00"}) {
                assertThat(tool.execute(Map.of("restaurant_id", restaurantId.toString(),
                    "reservation_at", when, "guest_count", 2)))
                    .as("format %s", when).contains("Réservation enregistrée");
            }
        }
    }

    @Test
    void execute_badDate_returnsMessage() {
        try (MockedStatic<SecurityHelper> sh = mockStatic(SecurityHelper.class)) {
            sh.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            assertThat(tool.execute(Map.of("restaurant_id", UUID.randomUUID().toString(),
                "reservation_at", "demain soir", "guest_count", 2)))
                .contains("Date invalide");
        }
    }
}
