package com.onesley.oneclick.modules.resource_booking.internal;

import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.ResourceDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test unitaire ISOLÉ (POJO, sans contexte Spring) — {@link Resource#toDto()} doit propager les
 * champs de planning ajoutés en V96 ({@code openingHours}, {@code slotDurationMinutes},
 * {@code maxInvitees}) au {@link ResourceDto} public consommé par les clients (Android natif / iOS).
 *
 * <p>Sans cette propagation, la grille de créneaux ne peut pas être générée côté client (régression
 * historique : le contrat exposait 8 champs et omettait le planning — cf migration V96).
 */
class ResourceScheduleDtoMappingTest {

    @Test
    void toDto_carriesScheduleFields() {
        Resource r = new Resource(UUID.randomUUID(), null, "padel_court", "Court 1");
        Map<String, List<String>> hours = Map.of(
            "mon", List.of("08:00-22:00"),
            "sat", List.of("08:00-22:00"));
        r.setOpeningHours(hours);
        r.setSlotDurationMinutes(90);
        r.setMaxInvitees(3);

        ResourceDto dto = r.toDto();

        assertThat(dto.slotDurationMinutes()).isEqualTo(90);
        assertThat(dto.maxInvitees()).isEqualTo(3);
        assertThat(dto.openingHours()).isEqualTo(hours);
        assertThat(dto.openingHours().get("mon")).containsExactly("08:00-22:00");
        // Les champs existants restent intacts.
        assertThat(dto.resourceType()).isEqualTo("padel_court");
        assertThat(dto.name()).isEqualTo("Court 1");
    }

    @Test
    void toDto_nullScheduleFields_remainNull() {
        Resource r = new Resource(UUID.randomUUID(), null, "spa_room", "Massage Suédois");

        ResourceDto dto = r.toDto();

        assertThat(dto.openingHours()).isNull();
        assertThat(dto.slotDurationMinutes()).isNull();
        assertThat(dto.maxInvitees()).isNull();
    }
}
