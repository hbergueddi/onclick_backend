package com.onesley.oneclick.service.marketing;

import com.onesley.oneclick.dto.marketing.ReferralDto;
import com.onesley.oneclick.mapper.marketing.ReferralMapper;
import com.onesley.oneclick.repository.marketing.ReferralRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link Referral} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class ReferralService {

    private final ReferralRepository repository;
    private final ReferralMapper mapper;

    public ReferralService(ReferralRepository repository, ReferralMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<ReferralDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<ReferralDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
