package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyTierRuleCreateDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyTierRulePatchDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link LoyaltyTierRuleService} (domaine paliers
 * plateforme, V43). Repository mocké — aucune DB, aucun contexte Spring.
 */
@ExtendWith(MockitoExtension.class)
class LoyaltyTierRuleServiceTest {

    @Mock LoyaltyTierRuleRepository repo;
    @InjectMocks LoyaltyTierRuleService service;

    @Test
    void list_mapsEntitiesToDtos() {
        LoyaltyTierRule r = new LoyaltyTierRule(UUID.randomUUID(), "Ruby");
        when(repo.findAllByDeletedAtIsNullOrderByMinTicketAsc()).thenReturn(List.of(r));
        var dtos = service.list();
        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).name()).isEqualTo("Ruby");
    }

    @Test
    void create_appliesAllFields() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        var dto = service.create(new LoyaltyTierRuleCreateDto(
            "Gold", "desc", "premium", new BigDecimal("0.2"), new BigDecimal("100"),
            300, "month", 1, 90, new BigDecimal("500"), true));
        assertThat(dto.name()).isEqualTo("Gold");
        assertThat(dto.type()).isEqualTo("premium");
        assertThat(dto.conversionRate()).isEqualByComparingTo("0.2");
        assertThat(dto.maxPointsPerTicket()).isEqualTo(300);
        assertThat(dto.enabled()).isTrue();
    }

    @Test
    void create_minimal_keepsEntityDefaults() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        var dto = service.create(new LoyaltyTierRuleCreateDto(
            "Bronze", null, null, null, null, null, null, null, null, null, null));
        assertThat(dto.name()).isEqualTo("Bronze");
        assertThat(dto.type()).isEqualTo("standard");
        assertThat(dto.enabled()).isTrue();
    }

    @Test
    void patch_notFound_throws() {
        when(repo.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.patch(UUID.randomUUID(),
            new LoyaltyTierRulePatchDto(null, null, null, null, null, null, null, null, null, null, false)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void patch_appliesNonNullOnly() {
        LoyaltyTierRule r = new LoyaltyTierRule(UUID.randomUUID(), "Ruby");
        when(repo.findByIdAndDeletedAtIsNull(r.getId())).thenReturn(Optional.of(r));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        var dto = service.patch(r.getId(),
            new LoyaltyTierRulePatchDto(null, null, null, null, null, null, null, null, null, null, false));
        assertThat(dto.name()).isEqualTo("Ruby");
        assertThat(dto.enabled()).isFalse();
    }

    @Test
    void delete_notFound_throws() {
        when(repo.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void delete_marksDeleted() {
        LoyaltyTierRule r = new LoyaltyTierRule(UUID.randomUUID(), "Ruby");
        when(repo.findByIdAndDeletedAtIsNull(r.getId())).thenReturn(Optional.of(r));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        service.delete(r.getId());
        assertThat(r.getDeletedAt()).isNotNull();
    }
}
