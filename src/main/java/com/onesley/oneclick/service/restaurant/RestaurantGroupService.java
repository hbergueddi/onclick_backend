package com.onesley.oneclick.service.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantGroupDto;
import com.onesley.oneclick.mapper.restaurant.RestaurantGroupMapper;
import com.onesley.oneclick.repository.restaurant.RestaurantGroupRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link RestaurantGroup} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class RestaurantGroupService {

    private final RestaurantGroupRepository repository;
    private final RestaurantGroupMapper mapper;

    public RestaurantGroupService(RestaurantGroupRepository repository, RestaurantGroupMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<RestaurantGroupDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<RestaurantGroupDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
