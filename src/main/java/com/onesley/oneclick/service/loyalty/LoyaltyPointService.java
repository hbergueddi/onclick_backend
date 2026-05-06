package com.onesley.oneclick.service.loyalty;

import com.onesley.oneclick.dto.loyalty.LoyaltyPointDto;
import com.onesley.oneclick.mapper.loyalty.LoyaltyPointMapper;
import com.onesley.oneclick.repository.loyalty.LoyaltyPointRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link LoyaltyPoint} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class LoyaltyPointService {

    private final LoyaltyPointRepository repository;
    private final LoyaltyPointMapper mapper;

    public LoyaltyPointService(LoyaltyPointRepository repository, LoyaltyPointMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<LoyaltyPointDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<LoyaltyPointDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
