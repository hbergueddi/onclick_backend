package com.onesley.oneclick.service.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantKpisViewDto;
import com.onesley.oneclick.mapper.restaurant.RestaurantKpisViewMapper;
import com.onesley.oneclick.repository.restaurant.RestaurantKpisViewRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link RestaurantKpisView} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class RestaurantKpisViewService {

    private final RestaurantKpisViewRepository repository;
    private final RestaurantKpisViewMapper mapper;

    public RestaurantKpisViewService(RestaurantKpisViewRepository repository, RestaurantKpisViewMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public List<RestaurantKpisViewDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
