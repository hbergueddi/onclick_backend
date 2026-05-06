package com.onesley.oneclick.service.loyalty;

import com.onesley.oneclick.dto.loyalty.RedemptionEventDto;
import com.onesley.oneclick.mapper.loyalty.RedemptionEventMapper;
import com.onesley.oneclick.repository.loyalty.RedemptionEventRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link RedemptionEvent} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class RedemptionEventService {

    private final RedemptionEventRepository repository;
    private final RedemptionEventMapper mapper;

    public RedemptionEventService(RedemptionEventRepository repository, RedemptionEventMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<RedemptionEventDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<RedemptionEventDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
