package com.onesley.oneclick.service.loyalty;

import com.onesley.oneclick.dto.loyalty.EliteRsvpDto;
import com.onesley.oneclick.mapper.loyalty.EliteRsvpMapper;
import com.onesley.oneclick.repository.loyalty.EliteRsvpRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link EliteRsvp} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class EliteRsvpService {

    private final EliteRsvpRepository repository;
    private final EliteRsvpMapper mapper;

    public EliteRsvpService(EliteRsvpRepository repository, EliteRsvpMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<EliteRsvpDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<EliteRsvpDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
