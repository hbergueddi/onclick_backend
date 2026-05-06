package com.onesley.oneclick.service.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantMediaDto;
import com.onesley.oneclick.mapper.restaurant.RestaurantMediaMapper;
import com.onesley.oneclick.repository.restaurant.RestaurantMediaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link RestaurantMedia} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class RestaurantMediaService {

    private final RestaurantMediaRepository repository;
    private final RestaurantMediaMapper mapper;

    public RestaurantMediaService(RestaurantMediaRepository repository, RestaurantMediaMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<RestaurantMediaDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<RestaurantMediaDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
