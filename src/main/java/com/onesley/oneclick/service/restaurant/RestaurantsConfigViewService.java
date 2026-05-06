package com.onesley.oneclick.service.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantsConfigViewDto;
import com.onesley.oneclick.mapper.restaurant.RestaurantsConfigViewMapper;
import com.onesley.oneclick.repository.restaurant.RestaurantsConfigViewRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link RestaurantsConfigView} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class RestaurantsConfigViewService {

    private final RestaurantsConfigViewRepository repository;
    private final RestaurantsConfigViewMapper mapper;

    public RestaurantsConfigViewService(RestaurantsConfigViewRepository repository, RestaurantsConfigViewMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public List<RestaurantsConfigViewDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
