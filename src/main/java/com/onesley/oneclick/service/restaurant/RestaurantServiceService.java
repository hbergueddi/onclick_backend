package com.onesley.oneclick.service.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantServiceDto;
import com.onesley.oneclick.mapper.restaurant.RestaurantServiceMapper;
import com.onesley.oneclick.repository.restaurant.RestaurantServiceRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link RestaurantService} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class RestaurantServiceService {

    private final RestaurantServiceRepository repository;
    private final RestaurantServiceMapper mapper;

    public RestaurantServiceService(RestaurantServiceRepository repository, RestaurantServiceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<RestaurantServiceDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<RestaurantServiceDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
