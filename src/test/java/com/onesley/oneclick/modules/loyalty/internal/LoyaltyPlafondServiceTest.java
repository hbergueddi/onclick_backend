package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyPlafondCreateDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyPlafondPatchDto;
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

/** Tests unitaires Mockito de {@link LoyaltyPlafondService} (plafonds Lounge, V44). */
@ExtendWith(MockitoExtension.class)
class LoyaltyPlafondServiceTest {

    @Mock LoyaltyPlafondRepository repo;
    @InjectMocks LoyaltyPlafondService service;

    @Test
    void list_mapsEntitiesToDtos() {
        LoyaltyPlafond p = new LoyaltyPlafond(UUID.randomUUID(), "Cap mensuel");
        when(repo.findAllByDeletedAtIsNullOrderByScopeAscCreatedAtAsc()).thenReturn(List.of(p));
        var dtos = service.list();
        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).name()).isEqualTo("Cap mensuel");
        assertThat(dtos.get(0).scope()).isEqualTo("global");
    }

    @Test
    void create_appliesFields() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        var dto = service.create(new LoyaltyPlafondCreateDto(
            "Cap client", "client", new BigDecimal("5000"), "points", "desc", true));
        assertThat(dto.name()).isEqualTo("Cap client");
        assertThat(dto.scope()).isEqualTo("client");
        assertThat(dto.value()).isEqualByComparingTo("5000");
    }

    @Test
    void patch_notFound_throws() {
        when(repo.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.patch(UUID.randomUUID(),
            new LoyaltyPlafondPatchDto(null, null, null, null, null, false)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void patch_appliesEnabled() {
        LoyaltyPlafond p = new LoyaltyPlafond(UUID.randomUUID(), "Cap");
        when(repo.findByIdAndDeletedAtIsNull(p.getId())).thenReturn(Optional.of(p));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        var dto = service.patch(p.getId(), new LoyaltyPlafondPatchDto(null, null, null, null, null, false));
        assertThat(dto.enabled()).isFalse();
    }

    @Test
    void delete_notFound_throws() {
        when(repo.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void delete_marksDeleted() {
        LoyaltyPlafond p = new LoyaltyPlafond(UUID.randomUUID(), "Cap");
        when(repo.findByIdAndDeletedAtIsNull(p.getId())).thenReturn(Optional.of(p));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        service.delete(p.getId());
        assertThat(p.getDeletedAt()).isNotNull();
    }
}
