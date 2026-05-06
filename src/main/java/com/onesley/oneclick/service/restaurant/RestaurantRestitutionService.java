package com.onesley.oneclick.service.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantRestitutionDto;
import com.onesley.oneclick.mapper.restaurant.RestaurantRestitutionMapper;
import com.onesley.oneclick.repository.restaurant.RestaurantRestitutionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link RestaurantRestitution} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class RestaurantRestitutionService {

    private final RestaurantRestitutionRepository repository;
    private final RestaurantRestitutionMapper mapper;

    public RestaurantRestitutionService(RestaurantRestitutionRepository repository, RestaurantRestitutionMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<RestaurantRestitutionDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<RestaurantRestitutionDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
