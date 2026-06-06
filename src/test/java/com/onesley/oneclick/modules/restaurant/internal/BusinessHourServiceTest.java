package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.restaurant.api.BusinessHourDtos.BusinessHourEntryDto;
import com.onesley.oneclick.modules.restaurant.api.BusinessHourDtos.BusinessHoursPutDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link BusinessHourService} (E1 — horaires d'ouverture).
 * Lecture (mapping + tri), remplacement (delete+insert), garde-fous (resto absent, end&lt;=start).
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class BusinessHourServiceTest {

    @Mock BusinessHourRepository repository;
    @Mock RestaurantRepository restaurantRepository;
    @InjectMocks BusinessHourService service;

    private Restaurant restaurant(UUID id) {
        Restaurant r = new Restaurant(id, new Tenant(UUID.randomUUID(), "T", "t"), "Resto", "Casablanca");
        return r;
    }

    private BusinessHour row(UUID restaurantId, int day, String open, String close) {
        return new BusinessHour(UUID.randomUUID(), "restaurant", restaurantId, day,
            LocalTime.parse(open), LocalTime.parse(close));
    }

    @Test
    void getForRestaurant_notFound_throwsNotFound() {
        when(restaurantRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getForRestaurant(UUID.randomUUID()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getForRestaurant_mapsRows() {
        UUID id = UUID.randomUUID();
        when(restaurantRepository.findById(id)).thenReturn(Optional.of(restaurant(id)));
        when(repository.findByEntityTypeAndEntityIdOrderByDayOfWeekAscStartTimeAsc("restaurant", id))
            .thenReturn(List.of(row(id, 1, "12:00", "15:00"), row(id, 1, "19:00", "23:00")));

        var result = service.getForRestaurant(id);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).dayOfWeek()).isEqualTo(1);
        assertThat(result.get(0).startTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(result.get(1).endTime()).isEqualTo(LocalTime.of(23, 0));
    }

    @Test
    void replaceForRestaurant_restaurantNotFound_throwsNotFound() {
        when(restaurantRepository.findById(any())).thenReturn(Optional.empty());
        var dto = new BusinessHoursPutDto(List.of(new BusinessHourEntryDto(1, LocalTime.of(12, 0), LocalTime.of(23, 0))));
        assertThatThrownBy(() -> service.replaceForRestaurant(UUID.randomUUID(), dto))
            .isInstanceOf(NotFoundException.class);
        verify(repository, never()).deleteByEntityTypeAndEntityId(any(), any());
    }

    @Test
    void replaceForRestaurant_endNotAfterStart_throwsBadRequest_noMutation() {
        UUID id = UUID.randomUUID();
        when(restaurantRepository.findById(id)).thenReturn(Optional.of(restaurant(id)));
        var dto = new BusinessHoursPutDto(List.of(new BusinessHourEntryDto(2, LocalTime.of(20, 0), LocalTime.of(20, 0))));
        assertThatThrownBy(() -> service.replaceForRestaurant(id, dto))
            .isInstanceOf(BadRequestException.class);
        verify(repository, never()).deleteByEntityTypeAndEntityId(any(), any());
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    void replaceForRestaurant_success_deletesThenInserts() {
        UUID id = UUID.randomUUID();
        when(restaurantRepository.findById(id)).thenReturn(Optional.of(restaurant(id)));
        when(repository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        var dto = new BusinessHoursPutDto(List.of(
            new BusinessHourEntryDto(1, LocalTime.of(12, 0), LocalTime.of(23, 0)),
            new BusinessHourEntryDto(2, LocalTime.of(12, 0), LocalTime.of(23, 0))
        ));
        var result = service.replaceForRestaurant(id, dto);

        verify(repository).deleteByEntityTypeAndEntityId(eq("restaurant"), eq(id));
        verify(repository).saveAll(anyList());
        assertThat(result).hasSize(2);
        assertThat(result.get(0).dayOfWeek()).isEqualTo(1);
    }

    @Test
    void replaceForRestaurant_emptyList_deletesAll_returnsEmpty() {
        UUID id = UUID.randomUUID();
        when(restaurantRepository.findById(id)).thenReturn(Optional.of(restaurant(id)));
        when(repository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        var result = service.replaceForRestaurant(id, new BusinessHoursPutDto(List.of()));
        verify(repository).deleteByEntityTypeAndEntityId(eq("restaurant"), eq(id));
        assertThat(result).isEmpty();
    }
}
