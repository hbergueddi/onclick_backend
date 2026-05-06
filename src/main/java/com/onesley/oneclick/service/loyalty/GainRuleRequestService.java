package com.onesley.oneclick.service.loyalty;

import com.onesley.oneclick.dto.loyalty.GainRuleRequestDto;
import com.onesley.oneclick.mapper.loyalty.GainRuleRequestMapper;
import com.onesley.oneclick.repository.loyalty.GainRuleRequestRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link GainRuleRequest} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class GainRuleRequestService {

    private final GainRuleRequestRepository repository;
    private final GainRuleRequestMapper mapper;

    public GainRuleRequestService(GainRuleRequestRepository repository, GainRuleRequestMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<GainRuleRequestDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<GainRuleRequestDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
