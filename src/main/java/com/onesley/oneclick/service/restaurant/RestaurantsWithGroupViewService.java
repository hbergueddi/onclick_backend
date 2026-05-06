package com.onesley.oneclick.service.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantsWithGroupViewDto;
import com.onesley.oneclick.mapper.restaurant.RestaurantsWithGroupViewMapper;
import com.onesley.oneclick.repository.restaurant.RestaurantsWithGroupViewRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link RestaurantsWithGroupView} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class RestaurantsWithGroupViewService {

    private final RestaurantsWithGroupViewRepository repository;
    private final RestaurantsWithGroupViewMapper mapper;

    public RestaurantsWithGroupViewService(RestaurantsWithGroupViewRepository repository, RestaurantsWithGroupViewMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public List<RestaurantsWithGroupViewDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
