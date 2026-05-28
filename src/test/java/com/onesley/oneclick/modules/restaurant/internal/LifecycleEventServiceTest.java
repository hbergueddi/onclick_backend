package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.core.identity.api.UserRepository;
import com.onesley.oneclick.modules.restaurant.api.LifecycleEventCreateDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link LifecycleEventService} (journal cycle de vie, V45).
 * Repos mockés — vérifie le mapping + la résolution intra-module du nom de restaurant.
 */
@ExtendWith(MockitoExtension.class)
class LifecycleEventServiceTest {

    @Mock LifecycleEventRepository repo;
    @Mock RestaurantRepository restaurantRepo;
    @Mock UserRepository userRepository;
    @InjectMocks LifecycleEventService service;

    @Test
    void list_resolvesRestaurantName() {
        UUID rid = UUID.randomUUID();
        LifecycleEvent e = new LifecycleEvent(UUID.randomUUID(), "validation");
        e.setRestaurantId(rid);
        e.setDetails("ok");
        e.setActor("Admin");
        when(repo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(e));

        Restaurant r = mock(Restaurant.class);
        when(r.getId()).thenReturn(rid);
        when(r.getName()).thenReturn("Chez X");
        when(restaurantRepo.findAllById(any())).thenReturn(List.of(r));

        var dtos = service.list();
        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).event()).isEqualTo("validation");
        assertThat(dtos.get(0).restaurantName()).isEqualTo("Chez X");
        assertThat(dtos.get(0).actor()).isEqualTo("Admin");
    }

    @Test
    void create_withRestaurant_resolvesName() {
        UUID rid = UUID.randomUUID();
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        Restaurant r = mock(Restaurant.class);
        when(r.getName()).thenReturn("Chez Y");
        when(restaurantRepo.findById(rid)).thenReturn(Optional.of(r));

        var dto = service.create(new LifecycleEventCreateDto("inscription", rid, "details", "actor"));
        assertThat(dto.event()).isEqualTo("inscription");
        assertThat(dto.restaurantName()).isEqualTo("Chez Y");
    }

    @Test
    void create_nullRestaurant_noLookup() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        var dto = service.create(new LifecycleEventCreateDto("global_event", null, null, null));
        assertThat(dto.event()).isEqualTo("global_event");
        assertThat(dto.restaurantName()).isNull();
        verify(restaurantRepo, never()).findById(any());
    }

    @Test
    void record_persistsEvent_withSystemActorWhenNoSecurityContext() {
        SecurityContextHolder.clearContext(); // pas de JWT → acteur « Système »
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        UUID rid = UUID.randomUUID();

        service.record("suspension", rid, "Changement de statut : active → paused");

        ArgumentCaptor<LifecycleEvent> captor = ArgumentCaptor.forClass(LifecycleEvent.class);
        verify(repo).save(captor.capture());
        LifecycleEvent saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("suspension");
        assertThat(saved.getRestaurantId()).isEqualTo(rid);
        assertThat(saved.getDetails()).contains("active → paused");
        assertThat(saved.getActor()).isEqualTo("Système");
        verify(userRepository, never()).findById(any()); // uid null → pas de lookup
    }
}
