package com.onesley.oneclick.service.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantTierConfigDto;
import com.onesley.oneclick.mapper.restaurant.RestaurantTierConfigMapper;
import com.onesley.oneclick.repository.restaurant.RestaurantTierConfigRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link RestaurantTierConfig} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class RestaurantTierConfigService {

    private final RestaurantTierConfigRepository repository;
    private final RestaurantTierConfigMapper mapper;

    public RestaurantTierConfigService(RestaurantTierConfigRepository repository, RestaurantTierConfigMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<RestaurantTierConfigDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<RestaurantTierConfigDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
