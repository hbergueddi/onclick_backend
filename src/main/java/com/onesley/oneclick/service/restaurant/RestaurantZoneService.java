package com.onesley.oneclick.service.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantZoneDto;
import com.onesley.oneclick.mapper.restaurant.RestaurantZoneMapper;
import com.onesley.oneclick.repository.restaurant.RestaurantZoneRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link RestaurantZone} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class RestaurantZoneService {

    private final RestaurantZoneRepository repository;
    private final RestaurantZoneMapper mapper;

    public RestaurantZoneService(RestaurantZoneRepository repository, RestaurantZoneMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<RestaurantZoneDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<RestaurantZoneDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
