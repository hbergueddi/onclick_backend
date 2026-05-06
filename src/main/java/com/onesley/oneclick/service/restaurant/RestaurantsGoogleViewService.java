package com.onesley.oneclick.service.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantsGoogleViewDto;
import com.onesley.oneclick.mapper.restaurant.RestaurantsGoogleViewMapper;
import com.onesley.oneclick.repository.restaurant.RestaurantsGoogleViewRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link RestaurantsGoogleView} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class RestaurantsGoogleViewService {

    private final RestaurantsGoogleViewRepository repository;
    private final RestaurantsGoogleViewMapper mapper;

    public RestaurantsGoogleViewService(RestaurantsGoogleViewRepository repository, RestaurantsGoogleViewMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public List<RestaurantsGoogleViewDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
