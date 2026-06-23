package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.shared.events.ResourceBookingStatusChangedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests unitaires isolés (Mockito) du listener loyalty {@link ResourceBookingPunchListener}.
 *
 * <p>Vérifie le flow event→punch : un booking {@code completed} sur un type mappé déclenche
 * {@code punchCardService.punch(tenant, organizer, activity)} ; aucun punch sinon (statut ≠
 * completed, type non mappé, tenant/organizer null). Aucune dépendance directe
 * resource_booking→loyalty : le listener consomme l'event shared.</p>
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class ResourceBookingPunchListenerTest {

    @Mock PunchCardService punchCardService;
    @InjectMocks ResourceBookingPunchListener listener;

    private ResourceBookingStatusChangedEvent event(UUID tenant, UUID organizer, String resourceType, String newStatus) {
        return new ResourceBookingStatusChangedEvent(
            UUID.randomUUID(), organizer, UUID.randomUUID(), tenant, resourceType,
            "pending", newStatus, UUID.randomUUID(), Instant.now());
    }

    @Test
    void completedMappedType_punchesCorrectActivity() {
        UUID tenant = UUID.randomUUID();
        UUID organizer = UUID.randomUUID();
        listener.onStatusChanged(event(tenant, organizer, "padel_court", "completed"));
        verify(punchCardService).punch(eq(tenant), eq(organizer), eq("padel"));
    }

    @Test
    void completedBarberChair_mapsToCoiffeur() {
        UUID tenant = UUID.randomUUID();
        UUID organizer = UUID.randomUUID();
        listener.onStatusChanged(event(tenant, organizer, "barber_chair", "completed"));
        verify(punchCardService).punch(eq(tenant), eq(organizer), eq("coiffeur"));
    }

    @Test
    void completedUnmappedType_noPunch() {
        listener.onStatusChanged(event(UUID.randomUUID(), UUID.randomUUID(), "seminar_room", "completed"));
        verify(punchCardService, never()).punch(any(), any(), any());
    }

    @Test
    void nonCompletedStatus_noPunch() {
        UUID tenant = UUID.randomUUID();
        UUID organizer = UUID.randomUUID();
        listener.onStatusChanged(event(tenant, organizer, "padel_court", "confirmed"));
        listener.onStatusChanged(event(tenant, organizer, "padel_court", "cancelled"));
        listener.onStatusChanged(event(tenant, organizer, "padel_court", "no_show"));
        verify(punchCardService, never()).punch(any(), any(), any());
    }

    @Test
    void completedButNullTenantOrOrganizer_noPunch() {
        listener.onStatusChanged(event(null, UUID.randomUUID(), "padel_court", "completed"));
        listener.onStatusChanged(event(UUID.randomUUID(), null, "padel_court", "completed"));
        verify(punchCardService, never()).punch(any(), any(), any());
    }
}
