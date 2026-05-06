package com.onesley.oneclick.service.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantTierStatuDto;
import com.onesley.oneclick.mapper.restaurant.RestaurantTierStatuMapper;
import com.onesley.oneclick.repository.restaurant.RestaurantTierStatuRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link RestaurantTierStatu} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class RestaurantTierStatuService {

    private final RestaurantTierStatuRepository repository;
    private final RestaurantTierStatuMapper mapper;

    public RestaurantTierStatuService(RestaurantTierStatuRepository repository, RestaurantTierStatuMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<RestaurantTierStatuDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<RestaurantTierStatuDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
