package com.onesley.oneclick.modules.store.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.store.api.StoreOnboardingDtos.OnboardingCreateDto;
import com.onesley.oneclick.modules.store.api.StoreOnboardingDtos.OnboardingDecisionDto;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class StoreOnboardingServiceTest {

    @Mock StoreOnboardingRepository repo;
    @InjectMocks StoreOnboardingService service;

    private StoreOnboardingRequest request() {
        StoreOnboardingRequest r = new StoreOnboardingRequest();
        r.setRestaurantName("Resto"); r.setOwnerEmail("o@x.ma"); r.setStatus("pending");
        return r;
    }

    @Test
    void findAll_withStatus_andWithout() {
        when(repo.findByStatus("pending")).thenReturn(List.of(request()));
        when(repo.findAllActive()).thenReturn(List.of(request(), request()));
        assertThat(service.findAll("pending")).hasSize(1);
        assertThat(service.findAll(null)).hasSize(2);
    }

    @Test
    void findById_notFoundAndFound() {
        when(repo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        StoreOnboardingRequest r = request();
        when(repo.findById(r.getId())).thenReturn(Optional.of(r));
        assertThat(service.findById(r.getId())).isNotNull();
    }

    @Test
    void create_success_persistsEnrollmentFields() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        var saved = service.create(new OnboardingCreateDto(UUID.randomUUID(), "Bistrot", "marocaine", "Casa",
            "12 rue X", "+212600", "Ada", "L", "ada@x.ma", "+212611",
            "€€", "Bistrot de quartier", "Propriétaire", "001234567890123", "12345678", "RC-1", "PAT-1", 80,
            List.of("Déjeuner", "Dîner")));
        assertThat(saved).isNotNull();
        // Les champs enrollment legacy (V58) doivent être persistés + exposés.
        assertThat(saved.ice()).isEqualTo("001234567890123");
        assertThat(saved.capacity()).isEqualTo(80);
        assertThat(saved.services()).containsExactly("Déjeuner", "Dîner");
    }

    @Test
    void decide_notFound_andSuccess() {
        when(repo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.decide(UUID.randomUUID(), new OnboardingDecisionDto("approved", null, UUID.randomUUID())))
            .isInstanceOf(NotFoundException.class);
        StoreOnboardingRequest r = request();
        when(repo.findById(r.getId())).thenReturn(Optional.of(r));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        service.decide(r.getId(), new OnboardingDecisionDto("rejected", "incomplet", UUID.randomUUID()));
        assertThat(r.getStatus()).isEqualTo("rejected");
        assertThat(r.getReviewedAt()).isNotNull();
    }
}
