package com.onesley.oneclick.service.loyalty;

import com.onesley.oneclick.dto.loyalty.ExpiredPointDto;
import com.onesley.oneclick.mapper.loyalty.ExpiredPointMapper;
import com.onesley.oneclick.repository.loyalty.ExpiredPointRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link ExpiredPoint} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class ExpiredPointService {

    private final ExpiredPointRepository repository;
    private final ExpiredPointMapper mapper;

    public ExpiredPointService(ExpiredPointRepository repository, ExpiredPointMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<ExpiredPointDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<ExpiredPointDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
