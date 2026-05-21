package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.loyalty.api.GainRuleRequestDto;
import com.onesley.oneclick.security.SecurityHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link GainRuleRequestService} (L3 — modules.loyalty).
 * Workflow demandes de règles de gain : create, approve (crée la GainRule), reject.
 */
@ExtendWith(MockitoExtension.class)
class GainRuleRequestServiceTest {

    @Mock GainRuleRequestRepository repository;
    @Mock GainRuleRepository gainRuleRepository;
    @InjectMocks GainRuleRequestService service;

    private GainRuleRequest req() {
        return new GainRuleRequest(UUID.randomUUID(), UUID.randomUUID(), "Promo", new BigDecimal("0.10"));
    }

    @Test
    void findAll_filtersDeleted() {
        GainRuleRequest live = req();
        GainRuleRequest deleted = req();
        ReflectionTestUtils.setField(deleted, "deletedAt", Instant.now());
        when(repository.findAll()).thenReturn(List.of(live, deleted));
        assertThat(service.findAll()).hasSize(1);
    }

    @Test
    void findPending_andByRestaurant_map() {
        when(repository.findAllByStatusAndDeletedAtIsNull("pending")).thenReturn(List.of(req()));
        UUID resto = UUID.randomUUID();
        when(repository.findAllByRestaurantIdAndDeletedAtIsNull(resto)).thenReturn(List.of(req()));
        assertThat(service.findPending()).hasSize(1);
        assertThat(service.findByRestaurant(resto)).hasSize(1);
    }

    @Test
    void findById_notFoundAndFound() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        GainRuleRequest r = req();
        when(repository.findById(r.getId())).thenReturn(Optional.of(r));
        assertThat(service.findById(r.getId())).isNotNull();
    }

    @Test
    void create_withTypeAndMinAmount_andWithout() {
        when(repository.save(any(GainRuleRequest.class))).thenAnswer(i -> i.getArgument(0));
        assertThat(service.create(new GainRuleRequestDto.CreateDto(
            UUID.randomUUID(), "Promo", "desc", "premium", new BigDecimal("0.20"), 50, 500, new BigDecimal("10.00")))).isNotNull();
        assertThat(service.create(new GainRuleRequestDto.CreateDto(
            UUID.randomUUID(), "Promo2", null, null, new BigDecimal("0.10"), null, null, null))).isNotNull();
    }

    @Test
    void approve_notFound_throwsNotFound() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.approve(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void approve_alreadyTreated_throwsBadRequest() {
        GainRuleRequest r = req();
        r.setStatus("approved");
        when(repository.findById(any())).thenReturn(Optional.of(r));
        assertThatThrownBy(() -> service.approve(r.getId())).isInstanceOf(BadRequestException.class);
    }

    @Test
    void approve_pending_createsGainRule_andMarksApproved() {
        GainRuleRequest r = req(); // status défaut "pending"
        when(repository.findById(any())).thenReturn(Optional.of(r));
        when(gainRuleRepository.save(any(GainRule.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.save(any(GainRuleRequest.class))).thenAnswer(i -> i.getArgument(0));
        try (MockedStatic<SecurityHelper> ignored = mockStatic(SecurityHelper.class)) {
            service.approve(r.getId());
        }
        assertThat(r.getStatus()).isEqualTo("approved");
        assertThat(r.getReviewedAt()).isNotNull();
        assertThat(r.getCreatedRuleId()).isNotNull();
    }

    @Test
    void reject_notFound_throwsNotFound() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.reject(UUID.randomUUID(), new GainRuleRequestDto.RejectDto("motif")))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void reject_alreadyTreated_throwsBadRequest() {
        GainRuleRequest r = req();
        r.setStatus("rejected");
        when(repository.findById(any())).thenReturn(Optional.of(r));
        assertThatThrownBy(() -> service.reject(r.getId(), new GainRuleRequestDto.RejectDto("motif")))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void reject_pending_marksRejectedWithReason() {
        GainRuleRequest r = req();
        when(repository.findById(any())).thenReturn(Optional.of(r));
        when(repository.save(any(GainRuleRequest.class))).thenAnswer(i -> i.getArgument(0));
        try (MockedStatic<SecurityHelper> ignored = mockStatic(SecurityHelper.class)) {
            service.reject(r.getId(), new GainRuleRequestDto.RejectDto("taux trop élevé"));
        }
        assertThat(r.getStatus()).isEqualTo("rejected");
        assertThat(r.getRejectionReason()).isEqualTo("taux trop élevé");
    }
}
