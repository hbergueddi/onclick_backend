package com.onesley.oneclick.service.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantExpiredPoolDto;
import com.onesley.oneclick.mapper.restaurant.RestaurantExpiredPoolMapper;
import com.onesley.oneclick.repository.restaurant.RestaurantExpiredPoolRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link RestaurantExpiredPool} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class RestaurantExpiredPoolService {

    private final RestaurantExpiredPoolRepository repository;
    private final RestaurantExpiredPoolMapper mapper;

    public RestaurantExpiredPoolService(RestaurantExpiredPoolRepository repository, RestaurantExpiredPoolMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<RestaurantExpiredPoolDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<RestaurantExpiredPoolDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
