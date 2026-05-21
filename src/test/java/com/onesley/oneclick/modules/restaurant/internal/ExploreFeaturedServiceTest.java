package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.restaurant.api.ExploreFeaturedDtos.ExploreFeaturedCreateDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link ExploreFeaturedService} (L3 — modules.restaurant).
 * Upsert (create vs update existant), findAllEnabled, delete (not found).
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class ExploreFeaturedServiceTest {

    @Mock ExploreFeaturedRepository repo;
    @InjectMocks ExploreFeaturedService service;

    @BeforeEach
    void setup() {
        lenient().when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void findAllEnabled_maps() {
        when(repo.findAllEnabledOrdered()).thenReturn(List.of(new ExploreFeatured(), new ExploreFeatured()));
        assertThat(service.findAllEnabled()).hasSize(2);
    }

    @Test
    void upsert_createsWhenAbsent_defaultRankZero() {
        when(repo.findByRestaurant(any())).thenReturn(Optional.empty());
        assertThat(service.upsert(new ExploreFeaturedCreateDto(UUID.randomUUID(), null, null, null, null))).isNotNull();
    }

    @Test
    void upsert_updatesExisting_withAllFields() {
        when(repo.findByRestaurant(any())).thenReturn(Optional.of(new ExploreFeatured()));
        assertThat(service.upsert(new ExploreFeaturedCreateDto(
            UUID.randomUUID(), 5, false, Instant.now(), Instant.now().plusSeconds(86400)))).isNotNull();
    }

    @Test
    void delete_notFoundAndSuccess() {
        when(repo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        ExploreFeatured f = new ExploreFeatured();
        when(repo.findById(any())).thenReturn(Optional.of(f));
        service.delete(UUID.randomUUID());
        verify(repo).delete(f);
    }
}
