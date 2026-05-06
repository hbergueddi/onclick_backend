package com.onesley.oneclick.service.loyalty;

import com.onesley.oneclick.dto.loyalty.LoyaltyPunchCardDto;
import com.onesley.oneclick.mapper.loyalty.LoyaltyPunchCardMapper;
import com.onesley.oneclick.repository.loyalty.LoyaltyPunchCardRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link LoyaltyPunchCard} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class LoyaltyPunchCardService {

    private final LoyaltyPunchCardRepository repository;
    private final LoyaltyPunchCardMapper mapper;

    public LoyaltyPunchCardService(LoyaltyPunchCardRepository repository, LoyaltyPunchCardMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<LoyaltyPunchCardDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<LoyaltyPunchCardDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
