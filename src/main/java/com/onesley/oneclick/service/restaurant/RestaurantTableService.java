package com.onesley.oneclick.service.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantTableDto;
import com.onesley.oneclick.mapper.restaurant.RestaurantTableMapper;
import com.onesley.oneclick.repository.restaurant.RestaurantTableRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link RestaurantTable} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class RestaurantTableService {

    private final RestaurantTableRepository repository;
    private final RestaurantTableMapper mapper;

    public RestaurantTableService(RestaurantTableRepository repository, RestaurantTableMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<RestaurantTableDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<RestaurantTableDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
