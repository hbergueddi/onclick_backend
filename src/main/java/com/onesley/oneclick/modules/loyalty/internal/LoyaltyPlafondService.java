package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyPlafondCreateDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyPlafondDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyPlafondPatchDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Service du domaine « plafonds / limites Lounge » (loyalty_plafonds).
 * CRUD admin-only (gating RBAC LOYALTY_CAP côté contrôleur). Soft delete.
 */
@Service
@RequiredArgsConstructor
public class LoyaltyPlafondService {

    private final LoyaltyPlafondRepository repository;

    @Transactional(readOnly = true)
    public List<LoyaltyPlafondDto> list() {
        return repository.findAllByDeletedAtIsNullOrderByScopeAscCreatedAtAsc()
            .stream().map(LoyaltyPlafond::toDto).toList();
    }

    @Transactional
    public LoyaltyPlafondDto create(LoyaltyPlafondCreateDto dto) {
        LoyaltyPlafond p = new LoyaltyPlafond(UUID.randomUUID(), dto.name().trim());
        if (dto.scope() != null) p.setScope(dto.scope());
        if (dto.value() != null) p.setValue(dto.value());
        if (dto.unit() != null) p.setUnit(dto.unit());
        if (dto.description() != null) p.setDescription(dto.description());
        if (dto.enabled() != null) p.setEnabled(dto.enabled());
        return repository.save(p).toDto();
    }

    @Transactional
    public LoyaltyPlafondDto patch(UUID id, LoyaltyPlafondPatchDto dto) {
        LoyaltyPlafond p = repository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("LoyaltyPlafond", id));
        if (dto.name() != null && !dto.name().isBlank()) p.setName(dto.name().trim());
        if (dto.scope() != null) p.setScope(dto.scope());
        if (dto.value() != null) p.setValue(dto.value());
        if (dto.unit() != null) p.setUnit(dto.unit());
        if (dto.description() != null) p.setDescription(dto.description());
        if (dto.enabled() != null) p.setEnabled(dto.enabled());
        return repository.save(p).toDto();
    }

    @Transactional
    public void delete(UUID id) {
        LoyaltyPlafond p = repository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("LoyaltyPlafond", id));
        p.markDeleted();
        repository.save(p);
    }
}
