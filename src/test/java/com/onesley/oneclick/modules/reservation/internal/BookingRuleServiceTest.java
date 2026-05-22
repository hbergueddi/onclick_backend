package com.onesley.oneclick.modules.reservation.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.reservation.api.BookingRuleDtos.BookingRuleCreateDto;
import com.onesley.oneclick.modules.reservation.api.BookingRuleDtos.BookingRulePatchDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests unitaires Mockito de {@link BookingRuleService} (L3 — modules.reservation). */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class BookingRuleServiceTest {

    @Mock BookingRuleRepository repository;
    @InjectMocks BookingRuleService service;

    @BeforeEach
    void setup() { lenient().when(repository.save(any())).thenAnswer(i -> i.getArgument(0)); }

    private BookingRule rule() { return new BookingRule(UUID.randomUUID(), UUID.randomUUID()); }

    @Test
    void findByRestaurant_maps() {
        when(repository.findAllByRestaurantId(any())).thenReturn(List.of(rule()));
        assertThat(service.findByRestaurant(UUID.randomUUID())).hasSize(1);
    }

    @Test
    void create_appliesFields() {
        assertThat(service.create(UUID.randomUUID(), new BookingRuleCreateDto(8, 90, 2))).isNotNull();
        assertThat(service.create(UUID.randomUUID(), new BookingRuleCreateDto(null, null, null))).isNotNull();
    }

    @Test
    void patch_notFoundAndSuccess() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.patch(UUID.randomUUID(), new BookingRulePatchDto(10, null, null)))
            .isInstanceOf(NotFoundException.class);
        BookingRule r = rule();
        when(repository.findById(r.getId())).thenReturn(Optional.of(r));
        service.patch(r.getId(), new BookingRulePatchDto(10, 120, 4));
        assertThat(r.getMaxGuest()).isEqualTo(10);
    }

    @Test
    void delete_notFoundAndSuccess() {
        when(repository.existsById(any())).thenReturn(false);
        assertThatThrownBy(() -> service.delete(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(true);
        service.delete(id);
        verify(repository).deleteById(id);
    }
}
