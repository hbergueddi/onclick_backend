package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.loyalty.api.RuleAssignmentDto;
import com.onesley.oneclick.modules.loyalty.api.RuleAssignmentResultDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link RuleAssignmentService} (Gap #1 — assignation
 * en masse d'une tier-rule plateforme aux restaurants). Repositories mockés —
 * aucune DB, aucun contexte Spring.
 */
@ExtendWith(MockitoExtension.class)
class RuleAssignmentServiceTest {

    @Mock GainRuleRepository gainRuleRepository;
    @Mock LoyaltyTierRuleRepository tierRuleRepository;
    @InjectMocks RuleAssignmentService service;

    private LoyaltyTierRule tierRule(UUID id) {
        LoyaltyTierRule r = new LoyaltyTierRule(id, "Gold");
        r.setConversionRate(new BigDecimal("0.2500"));
        r.setMinTicket(new BigDecimal("80.00"));
        r.setMaxPointsPerTicket(300);
        r.setEnabled(true);
        return r;
    }

    // ─── assign ───────────────────────────────────────────────────────────────

    @Test
    void assign_createsGainRuleWhenAbsent_copiesFieldsAndSource() {
        UUID tierId = UUID.randomUUID();
        UUID restoId = UUID.randomUUID();
        when(tierRuleRepository.findByIdAndDeletedAtIsNull(tierId)).thenReturn(Optional.of(tierRule(tierId)));
        when(gainRuleRepository.findByRestaurantIdAndDeletedAtIsNull(restoId)).thenReturn(Optional.empty());
        when(gainRuleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RuleAssignmentResultDto result = service.assign(tierId, List.of(restoId));

        assertThat(result.affected()).isEqualTo(1);
        ArgumentCaptor<GainRule> captor = ArgumentCaptor.forClass(GainRule.class);
        verify(gainRuleRepository).save(captor.capture());
        GainRule saved = captor.getValue();
        assertThat(saved.getRestaurantId()).isEqualTo(restoId);
        assertThat(saved.getConversionRate()).isEqualByComparingTo("0.2500");
        assertThat(saved.getMinAmount()).isEqualByComparingTo("80.00");
        assertThat(saved.getCapPerVisit()).isEqualTo(300);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getSourceTierRuleId()).isEqualTo(tierId);
    }

    @Test
    void assign_updatesExistingGainRuleInPlace() {
        UUID tierId = UUID.randomUUID();
        UUID restoId = UUID.randomUUID();
        GainRule existing = new GainRule(UUID.randomUUID(), restoId, new BigDecimal("0.1000"));
        when(tierRuleRepository.findByIdAndDeletedAtIsNull(tierId)).thenReturn(Optional.of(tierRule(tierId)));
        when(gainRuleRepository.findByRestaurantIdAndDeletedAtIsNull(restoId)).thenReturn(Optional.of(existing));
        when(gainRuleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RuleAssignmentResultDto result = service.assign(tierId, List.of(restoId));

        assertThat(result.affected()).isEqualTo(1);
        assertThat(existing.getConversionRate()).isEqualByComparingTo("0.2500");
        assertThat(existing.getCapPerVisit()).isEqualTo(300);
        assertThat(existing.getSourceTierRuleId()).isEqualTo(tierId);
    }

    @Test
    void assign_tierRuleNotFound_throws() {
        UUID tierId = UUID.randomUUID();
        when(tierRuleRepository.findByIdAndDeletedAtIsNull(tierId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assign(tierId, List.of(UUID.randomUUID())))
            .isInstanceOf(NotFoundException.class);
        verify(gainRuleRepository, never()).save(any());
    }

    @Test
    void assign_emptyList_returnsZero_noWrites() {
        UUID tierId = UUID.randomUUID();
        when(tierRuleRepository.findByIdAndDeletedAtIsNull(tierId)).thenReturn(Optional.of(tierRule(tierId)));
        assertThat(service.assign(tierId, List.of()).affected()).isZero();
        verify(gainRuleRepository, never()).save(any());
    }

    // ─── unassign ───────────────────────────────────────────────────────────────

    @Test
    void unassign_deletesMatchedRows() {
        UUID tierId = UUID.randomUUID();
        UUID r1 = UUID.randomUUID();
        UUID r2 = UUID.randomUUID();
        List<GainRule> linked = List.of(
            new GainRule(UUID.randomUUID(), r1, new BigDecimal("0.2")),
            new GainRule(UUID.randomUUID(), r2, new BigDecimal("0.2")));
        when(gainRuleRepository.findBySourceTierRuleIdAndRestaurantIdInAndDeletedAtIsNull(tierId, List.of(r1, r2)))
            .thenReturn(linked);

        RuleAssignmentResultDto result = service.unassign(tierId, List.of(r1, r2));

        assertThat(result.affected()).isEqualTo(2);
        verify(gainRuleRepository).deleteAll(linked);
    }

    @Test
    void unassign_emptyList_returnsZero_noInteraction() {
        assertThat(service.unassign(UUID.randomUUID(), List.of()).affected()).isZero();
        verifyNoInteractions(gainRuleRepository);
    }

    // ─── sync ───────────────────────────────────────────────────────────────────

    @Test
    void sync_recopiesFieldsToAllLinked() {
        UUID tierId = UUID.randomUUID();
        GainRule g1 = new GainRule(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("0.1"));
        GainRule g2 = new GainRule(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("0.1"));
        when(tierRuleRepository.findByIdAndDeletedAtIsNull(tierId)).thenReturn(Optional.of(tierRule(tierId)));
        when(gainRuleRepository.findBySourceTierRuleIdAndDeletedAtIsNull(tierId)).thenReturn(List.of(g1, g2));
        when(gainRuleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RuleAssignmentResultDto result = service.sync(tierId);

        assertThat(result.affected()).isEqualTo(2);
        verify(gainRuleRepository, times(2)).save(any());
        assertThat(g1.getConversionRate()).isEqualByComparingTo("0.2500");
        assertThat(g2.getCapPerVisit()).isEqualTo(300);
    }

    @Test
    void sync_tierRuleNotFound_throws() {
        UUID tierId = UUID.randomUUID();
        when(tierRuleRepository.findByIdAndDeletedAtIsNull(tierId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.sync(tierId)).isInstanceOf(NotFoundException.class);
    }

    // ─── reads ───────────────────────────────────────────────────────────────────

    @Test
    void getAssignments_mapsRestaurantIdAndEnabled() {
        UUID tierId = UUID.randomUUID();
        UUID restoId = UUID.randomUUID();
        GainRule g = new GainRule(UUID.randomUUID(), restoId, new BigDecimal("0.2"));
        g.setActive(true);
        when(gainRuleRepository.findBySourceTierRuleIdAndDeletedAtIsNull(tierId)).thenReturn(List.of(g));

        List<RuleAssignmentDto> dtos = service.getAssignments(tierId);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).restaurantId()).isEqualTo(restoId);
        assertThat(dtos.get(0).enabled()).isTrue();
    }

    @Test
    void assignmentCounts_buildsMapFromGroupBy() {
        UUID t1 = UUID.randomUUID();
        UUID t2 = UUID.randomUUID();
        when(gainRuleRepository.countAssignmentsBySourceTierRule())
            .thenReturn(List.of(new Object[]{t1, 3L}, new Object[]{t2, 1L}));

        Map<UUID, Long> counts = service.assignmentCounts();

        assertThat(counts).containsEntry(t1, 3L).containsEntry(t2, 1L);
    }
}
