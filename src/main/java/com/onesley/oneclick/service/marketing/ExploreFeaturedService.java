package com.onesley.oneclick.service.marketing;

import com.onesley.oneclick.dto.marketing.ExploreFeaturedDto;
import com.onesley.oneclick.mapper.marketing.ExploreFeaturedMapper;
import com.onesley.oneclick.repository.marketing.ExploreFeaturedRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link ExploreFeatured} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class ExploreFeaturedService {

    private final ExploreFeaturedRepository repository;
    private final ExploreFeaturedMapper mapper;

    public ExploreFeaturedService(ExploreFeaturedRepository repository, ExploreFeaturedMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<ExploreFeaturedDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<ExploreFeaturedDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
