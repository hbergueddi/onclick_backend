package com.onesley.oneclick.service.misc;

import com.onesley.oneclick.dto.misc.LoyaltyPlafondDto;
import com.onesley.oneclick.mapper.misc.LoyaltyPlafondMapper;
import com.onesley.oneclick.repository.misc.LoyaltyPlafondRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link LoyaltyPlafond} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class LoyaltyPlafondService {

    private final LoyaltyPlafondRepository repository;
    private final LoyaltyPlafondMapper mapper;

    public LoyaltyPlafondService(LoyaltyPlafondRepository repository, LoyaltyPlafondMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<LoyaltyPlafondDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<LoyaltyPlafondDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
